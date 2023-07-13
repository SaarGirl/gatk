/**
 * $Id: SemiGlobalAligner.java 73459 2008-10-10 16:10:03Z tsharpe $
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
import edu.mit.broad.tedsUtils.align.Alignment.Block;
import edu.mit.broad.tedsUtils.align.Alignment.BlockType;

/**
 * Sequence X, in it's entirety, is optimally aligned to some portion of the Y sequence.
 * The alignment does not penalize as a gap the "unused" leading or trailing portions of
 * the Y sequence.
 *
 * @author tsharpe
 * @version $Revision$
 */
public class SemiGlobalAligner
    extends Aligner
{
    public SemiGlobalAligner( CharSequence seqX, CharSequence seqY )
    {
        super(seqX,seqY,new Scorer(),false);
    }

    public SemiGlobalAligner( CharSequence seqX, CharSequence seqY, Scorer scorer )
    {
        super(seqX,seqY,scorer,false);
    }

    public SemiGlobalAligner( CharSequence seqX, CharSequence seqY, Scorer scorer, boolean lateGapping )
    {
        super(seqX,seqY,scorer,lateGapping);
    }

    @Override
    public Score getScore()
    {
        AffineGapScorer yGap = mScorer.getYGapScorer();
        return getScore(new SemiGlobalScore(),0.F,0.F,yGap.getOpenScore(),yGap.getExtendScore());
    }

    @Override
    public Alignment getAlignment()
    {
        AffineGapScorer yGap = mScorer.getYGapScorer();
        Score score = new SemiGlobalScore();
        LinkedList<Block> blocks = getAlignment(score,0.F,0.F,yGap.getOpenScore(),yGap.getExtendScore());
        if ( blocks.getFirst().getType() == BlockType.X_GAP )
        {
            blocks.removeFirst();
        }
        return new Alignment(score.getScore(),blocks,new CharSubSequence(mSeqX,0,mSeqX.length()),new CharSubSequence(mSeqY,0,mSeqY.length()));
    }

    @Override
    public Aligner clone( CharSequence seqX, CharSequence seqY )
    {
        return new SemiGlobalAligner(seqX,seqY,getScorer(),isLateGapping());
    }

    static class SemiGlobalScore
        extends Score
    {
        @Override
        protected void checkLastRow( float[] scores, int yIdx )
        {
            // last row isn't interesting: nothing to do
        }
    }
}