/**
 * $Id: SomewhatGlobalAligner.java 73459 2008-10-10 16:10:03Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package edu.mit.broad.tedsUtils.align;

import java.util.LinkedList;

import edu.mit.broad.tedsUtils.CharSubSequence;
import edu.mit.broad.tedsUtils.SysProp;
import edu.mit.broad.tedsUtils.align.Alignment.Block;
import edu.mit.broad.tedsUtils.align.Alignment.BlockType;

/**
 * A substantial prefix of sequence X is aligned to a suffix of sequence Y.
 * We try to align X in its entirety against Y, but we'll allow an ending gap
 * of reasonable length on X if sequence Y has "run out".
 *
 * @author tsharpe
 * @version $Revision$
 */
public class SomewhatGlobalAligner
    extends Aligner
{
    public SomewhatGlobalAligner( CharSequence seqX, CharSequence seqY )
    {
        this(seqX,seqY,new Scorer());
    }

    public SomewhatGlobalAligner( CharSequence seqX, CharSequence seqY, Scorer scorer )
    {
        this(seqX,seqY,scorer,(int)Math.ceil(seqX.length()*MIN_X_FRACT));
    }

    public SomewhatGlobalAligner( CharSequence seqX, CharSequence seqY, Scorer scorer, int xLen )
    {
        super(seqX,seqY,scorer,false);
        mScore = new SomewhatGlobalScore(xLen);
    }

    public SomewhatGlobalAligner( CharSequence seqX, CharSequence seqY, Scorer scorer, int xLen, boolean lateGapping )
    {
        super(seqX,seqY,scorer,lateGapping);
        mScore = new SomewhatGlobalScore(xLen);
    }

    @Override
    public Score getScore()
    {
        AffineGapScorer yGap = mScorer.getYGapScorer();
        return getScore(mScore,0.F,0.F,yGap.getOpenScore(),yGap.getExtendScore());
    }

    @Override
    public Alignment getAlignment()
    {
        AffineGapScorer yGap = mScorer.getYGapScorer();
        LinkedList<Block> blocks = getAlignment(mScore,0.F,0.F,yGap.getOpenScore(),yGap.getExtendScore());
        if ( blocks.getFirst().getType() == BlockType.X_GAP )
        {
            blocks.removeFirst();
        }
        return new Alignment(mScore.getScore(),blocks,new CharSubSequence(mSeqX,0,mSeqX.length()),new CharSubSequence(mSeqY,0,mSeqY.length()));
    }

    @Override
    public Aligner clone( CharSequence seqX, CharSequence seqY )
    {
        return new SomewhatGlobalAligner(seqX,seqY,getScorer(),mScore.getMinXLen(),isLateGapping());
    }

    private SomewhatGlobalScore mScore;
    private static final double MIN_X_FRACT = SysProp.dVal("MinXFract",.75F);

    static class SomewhatGlobalScore
        extends Score
    {
        /**
         * Specify the minimum length of sequence X to be included in the alignment.
         */
        public SomewhatGlobalScore( int minXLen )
        {
            if ( minXLen < 0 )
            {
                throw new IllegalArgumentException("Minimum X length must be non-negative.");
            }
            mXStart = minXLen;
        }

        public int getMinXLen()
        {
            return mXStart;
        }

        @Override
        protected void checkLastRow( float[] scores, int yIdx )
        {
            for ( int xIdx = mXStart; xIdx < scores.length; ++xIdx )
            {
                checkScore(scores[xIdx], xIdx, yIdx);
            }
        }

        int mXStart;
    }
}