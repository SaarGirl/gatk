/**
 * $Id: Alignment.java 74570 2008-10-30 20:23:05Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package edu.mit.broad.tedsUtils.align;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import edu.mit.broad.tedsUtils.CharSubSequence;
import edu.mit.broad.tedsUtils.SysProp;

/**
 * An optimal alignment.
 *
 * @author tsharpe
 * @version $Revision$
 */
public class Alignment
{
    Alignment( float score, List<Block> blocks, CharSubSequence alignedXSeq, CharSubSequence alignedYSeq )
    {
        if ( blocks == null || blocks.size() < 1 )
        {
            throw new IllegalArgumentException("Empty list of blocks.");
        }
        mScore = score;
        mBlocks = blocks;
        mXSeq = alignedXSeq;
        mYSeq = alignedYSeq;
        for ( Block block : mBlocks )
        {
            if ( block.getType() != BlockType.PAIRWISE )
            {
                mNGaps += 1;
            }
            else
            {
                mDiagLen += block.getBlockLength();
            }
            mNMatches += block.getNMatches();
            mAlignmentLen += block.getBlockLength();
        }

        setSymbols(DFLT_SYMBOLS);
        setLineLength(LINE_LENGTH);
    }

    /**
     * The score of this alignment.
     */
    public float getScore()
    {
        return mScore;
    }

    /**
     * Gives you a newly allocated array of blocks in increasing-coordinate order.
     */
    public Block[] getBlocks()
    {
        return mBlocks.toArray(new Block[mBlocks.size()]);
    }

    /**
     * X coordinate of the first cell in the alignment.
     */
    public int getXStartingIndex()
    {
        return mBlocks.get(0).getXStartingIndex();
    }

    /**
     * X coordinate just beyond the last cell in the alignment.
     */
    public int getXEndingIndex()
    {
        return mBlocks.get(mBlocks.size()-1).getXEndingIndex();
    }

    /**
     * The aligned portion of the X sequence.
     */
    public CharSubSequence getAlignedXSequence()
    {
        return mXSeq;
    }

    /**
     * Y coordinate of the first cell in the alignment.
     */
    public int getYStartingIndex()
    {
        return mBlocks.get(0).getYStartingIndex();
    }

    /**
     * Y coordinate just beyond the last cell in the alignment.
     */
    public int getYEndingIndex()
    {
        return mBlocks.get(mBlocks.size()-1).getYEndingIndex();
    }

    /**
     * The aligned portion of the Y sequence.
     */
    public CharSubSequence getAlignedYSequence()
    {
        return mYSeq;
    }

    /**
     * The number of gaps (X or Y) in the alignment.
     */
    public int getNGaps()
    {
        return mNGaps;
    }

    /**
     * The total length of the alignment.
     * All matches, mismatches, and gaps.
     */
    public int getAlignmentLen()
    {
        return mAlignmentLen;
    }

    /**
     * The number of matches.
     * Remember that ambiguity codes might mean that matching bases won't be
     * represented by the same letter.  A match is anything with a non-negative
     * score in the scoring matrix.
     */
    public int getNMatches()
    {
        return mNMatches;
    }

    /**
     * The total length of pairwise blocks.
     * Includes matches and mismatches, but not gaps.
     */
    public int getDiagLen()
    {
        return mDiagLen;
    }

    /**
     * Returns matches/alignmentLen.
     * Fraction of bases in the alignment that match.
     */
    public double getIdentityFraction()
    {
        return (double)mNMatches/mAlignmentLen;
    }

    /**
     * Returns matches/diagLen.
     * I.e., the identity fraction, ignoring indels.
     */
    public double getPairwiseIdentityFraction()
    {
        return (double)mNMatches/mDiagLen;
    }

    /**
     * The symbols used for printing out the alignment track.
     */
    public String getSymbols()
    {
        return mSymbols;
    }

    /**
     * The symbols string is a four-character string that defines the symbols to use for the alignment track.
     * symbols.charAt(0) is the character to print for matches (' ' by default).
     * symbols.charAt(1) is the character to print for mismatches ('x' by default).
     * symbols.charAt(2) is the character to print for gaps ('-' by default).
     * symbols.charAt(3) is the character to print for end-gaps (' ' by default).
     */
    public void setSymbols( String symbols )
    {
        if ( symbols == null || symbols.length() != 4 )
        {
            throw new IllegalArgumentException("symbols must be a 4-character string.");
        }
        mSymbols = symbols;
    }

    /**
     * The line-length to use in printing out alignments.
     * This is the number of bases per line -- the printout of the coordinates is additional.
     */
    public int getLineLength()
    {
        return mLineLength;
    }

    /**
     * Set the line-length to use in printing out alignments.
     * This is the number of bases per line -- the printout of the coordinates is additional.
     */
    public void setLineLength( int lineLength )
    {
        if ( lineLength <= 0 )
        {
            throw new IllegalArgumentException("lineLength must be positive.");
        }
        mLineLength = lineLength;
    }

    /**
     * Write the alignment.
     * Returns true if everything went OK.
     */
    public boolean print( PrintStream ps )
    {
        if ( ps == null )
        {
            throw new IllegalArgumentException("printstream cannot be null.");
        }
        write( new PrintStreamSink(ps) );
        ps.flush();
        return !ps.checkError();
    }

    /**
     * Write the alignment.
     */
    public void print( Writer writer )
        throws IOException
    {
        if ( writer == null )
        {
            throw new IllegalArgumentException("writer cannot be null.");
        }
        WriterSink ws = new WriterSink(writer);
        write(ws);
        if ( ws.getException() != null )
        {
            throw ws.getException();
        }
        writer.flush();
    }

    /**
     * The X sequence as aligned, with gaps inserted.
     */
    public String getXTrack()
    {
        StringBuilder sb = new StringBuilder(getAlignmentLen());
        CharIterator itr = new CharIterator(mSymbols,mBlocks);
        while ( itr.hasNext() )
        {
            sb.append(itr.nextXTrackChar());
        }
        return sb.toString();
    }

    /**
     * The index into the X track of a given X sequence index.
     * Returns NOT_FOUND if the xIdx is not part of the alignment.
     */
    public int getXTrackIndex( int xIdx )
    {
        int offset = 0;
        for ( int idx = mBlocks.size()-1; idx >= 0; --idx )
        {
            Block block = mBlocks.get(idx);
            if ( block.getXStartingIndex() <= xIdx && block.getXEndingIndex() >= xIdx )
            {
                return offset + (xIdx - block.getYStartingIndex());
            }
            offset += block.getBlockLength();
        }
        return NOT_FOUND;
    }

    /**
     * The index into the X sequence of a given base in the Y sequence.
     * Returns NOT_FOUND if the yIdx is not part of the alignment.
     * Note:  If the Y base is aligned to a gap in the X sequence, the result
     * returned will also be NOT_FOUND.
     */
    public int getXIndexForYIndex( int yIdx )
    {
        for ( int idx = mBlocks.size()-1; idx >= 0; --idx )
        {
            Block block = mBlocks.get(idx);
            if ( block.getYStartingIndex() > yIdx )
            {
                break;
            }
            if ( block.getType() == BlockType.PAIRWISE && block.getYEndingIndex() > yIdx )
            {
                return block.getXStartingIndex() + (yIdx - block.getYStartingIndex());
            }
        }
        return NOT_FOUND;
    }

    /**
     * The Y sequence as aligned, with gaps inserted.
     */
    public String getYTrack()
    {
        StringBuilder sb = new StringBuilder(getAlignmentLen());
        CharIterator itr = new CharIterator(mSymbols,mBlocks);
        while ( itr.hasNext() )
        {
            sb.append(itr.nextYTrackChar());
        }
        return sb.toString();
    }

    /**
     * The index into the Y track of a given Y sequence index.
     * Returns NOT_FOUND if the yIdx is not part of the alignment.
     */
    public int getYTrackIndex( int yIdx )
    {
        int offset = 0;
        for ( int idx = mBlocks.size()-1; idx >= 0; --idx )
        {
            Block block = mBlocks.get(idx);
            if ( block.getYStartingIndex() <= yIdx && block.getYEndingIndex() >= yIdx )
            {
                return offset + (yIdx - block.getYStartingIndex());
            }
            offset += block.getBlockLength();
        }
        return NOT_FOUND;
    }

    /**
     * The index into the Y sequence of a given base in the X sequence.
     * Returns NOT_FOUND if the xIdx is not part of the alignment.
     * Note:  If the X base is aligned to a gap in the Y sequence, the result
     * returned will also be NOT_FOUND.
     */
    public int getYIndexForXIndex( int xIdx )
    {
        for ( int idx = mBlocks.size()-1; idx >= 0; --idx )
        {
            Block block = mBlocks.get(idx);
            if ( block.getXStartingIndex() > xIdx )
            {
                break;
            }
            if ( block.getType() == BlockType.PAIRWISE && block.getXEndingIndex() > xIdx )
            {
                return block.getYStartingIndex() + (xIdx - block.getXStartingIndex());
            }
        }
        return NOT_FOUND;
    }

    /**
     * The alignment track (the middle track that shows the match-ups).
     */
    public String getATrack()
    {
        StringBuilder sb = new StringBuilder(getAlignmentLen());
        CharIterator itr = new CharIterator(mSymbols,mBlocks);
        while ( itr.hasNext() )
        {
            sb.append(itr.nextATrackChar());
        }
        return sb.toString();
    }

    /**
     * The usual 3-track display of an alignment.
     */
    @Override
    public String toString()
    {
        int nLinesPerTrack = (mAlignmentLen + mLineLength - 1) / mLineLength;
        return write(new StringSink(3*mAlignmentLen+15*nLinesPerTrack+16)).toString();
    }

    private LineSink write( LineSink ls )
    {
        StringBuilder sb = new StringBuilder();
        CharIterator xTrackItr = new CharIterator(mSymbols,mBlocks);
        if ( !xTrackItr.hasNext() )
            return ls; // EARLY RETURN!

        CharIterator aTrackItr = new CharIterator(mSymbols,mBlocks);
        CharIterator yTrackItr = new CharIterator(mSymbols,mBlocks);
        int idx = 0;
        while ( xTrackItr.hasNext() )
        {
            sb.append(xTrackItr.nextXTrackChar());
            if ( ++idx % mLineLength == 0 )
            {
                sb.append(' ');
                sb.append(xTrackItr.getXOffset()+1);
                if ( !xTrackItr.hasNext() )
                {
                    sb.append(" of ");
                    sb.append(mXSeq.getOriginalSequence().length());
                }
                sb.append('\n');
                ls.write(sb.toString());
                sb.setLength(0);

                for ( int iii = 0; iii < mLineLength; ++iii )
                    sb.append(aTrackItr.nextATrackChar());
                sb.append('\n');
                ls.write(sb.toString());
                sb.setLength(0);

                for ( int iii = 0; iii < mLineLength; ++iii )
                    sb.append(yTrackItr.nextYTrackChar());
                sb.append(' ');
                sb.append(yTrackItr.getYOffset()+1);
                if ( xTrackItr.hasNext() )
                {
                    sb.append('\n');
                }
                else
                {
                    sb.append(" of ");
                    sb.append(mYSeq.getOriginalSequence().length());
                }
                sb.append('\n');
                ls.write(sb.toString());
                sb.setLength(0);
            }
        }
        if ( idx % mLineLength != 0 )
        {
            sb.append(' ');
            sb.append(xTrackItr.getXOffset()+1);
            sb.append(" of ");
            sb.append(mXSeq.getOriginalSequence().length());
            sb.append('\n');
            ls.write(sb.toString());
            sb.setLength(0);

            while ( aTrackItr.hasNext() )
                sb.append(aTrackItr.nextATrackChar());
            sb.append('\n');
            ls.write(sb.toString());
            sb.setLength(0);

            while ( yTrackItr.hasNext() )
                sb.append(yTrackItr.nextYTrackChar());
            sb.append(' ');
            sb.append(yTrackItr.getYOffset()+1);
            sb.append(" of ");
            sb.append(mYSeq.getOriginalSequence().length());
            sb.append('\n');
            ls.write(sb.toString());
        }

        return ls;
    }

    private float mScore;
    private List<Block> mBlocks;
    private CharSubSequence mXSeq;
    private CharSubSequence mYSeq;
    private String mSymbols;
    private int mLineLength;
    private int mNGaps;
    private int mNMatches;
    private int mDiagLen;
    private int mAlignmentLen;

    public static final int NOT_FOUND = -1;

    private static final String DFLT_SYMBOLS = SysProp.sVal("AlignmentSymbols"," x- ");
    private static final int LINE_LENGTH = SysProp.iVal("AlignmentLineLength",100);

    public static enum BlockType
    {
        X_GAP, // there's Y sequence matched to nothing in X
        Y_GAP, // there's X sequence matched to nothing in Y 
        PAIRWISE; // there's a base-by-base match-up between X and Y
    }

    /**
     * An alignment block.
     * There are 3 types of block, enumerated by BlockType:  vertical movement through
     * the traceback array (an X-gap), horizontal movement (a Y-gap), or diagonal movement
     * (pairwise matches and mismatches).
     * In the pairwise case, the end coordinates are just past the end of the matchup (to
     * reflect the usual Java convention for sub-parts, so that (end-start) gives you the
     * length of the matching parts.  In the gap case, one of the coordinates has the same
     * starting and ending position (reflecting the 0 length of the matchup), and this is
     * the index of the base BEFORE WHICH the insertion occurs.  (Insertions occur between
     * bases, so you have a choice of whether to give the coordinate after which it occurs,
     * or before which it occurs -- we choose to give the coordinate before which the insertion
     * occurs.)
     */
    public static class Block
    {
        public Block( CharSubSequence xSeq, CharSubSequence ySeq, int nMatches )
        {
            mXSeq = xSeq;
            mYSeq = ySeq;
            mNMatches = nMatches;
            int xLen = mXSeq.length();
            int yLen = mYSeq.length();
            if ( xLen > 0 && yLen > 0 && xLen != yLen )
            {
                throw new IllegalArgumentException("pairwise block has unequal-length sequences");
            }
        }

        public BlockType getType()
        {
            BlockType result;
            if ( mXSeq.length() == 0 )
                result = BlockType.X_GAP;
            else if ( mYSeq.length() == 0 )
                result = BlockType.Y_GAP;
            else
                result = BlockType.PAIRWISE;
            return result;
        }

        public int getXStartingIndex()
        {
            return mXSeq.getStart();
        }

        public int getXEndingIndex()
        {
            return mXSeq.getEnd();
        }

        public CharSequence getXSequence()
        {
            CharSequence result = mXSeq;
            if ( result.length() == 0 )
            {
                result = getHyphens(mYSeq.length());
            }
            return result;
        }

        public int getYStartingIndex()
        {
            return mYSeq.getStart();
        }

        public int getYEndingIndex()
        {
            return mYSeq.getEnd();
        }

        public CharSequence getYSequence()
        {
            CharSequence result = mYSeq;
            if ( result.length() == 0 )
            {
                result = getHyphens(mXSeq.length());
            }
            return result;
        }

        public int getBlockLength()
        {
            return Math.max(mXSeq.length(),mYSeq.length());
        }

        public int getNMatches()
        {
            return mNMatches;
        }

        public double getIdentityFraction()
        {
            return (double)mNMatches / getBlockLength();
        }

        /**
         * Match or mismatch.
         * If this is not a pairwise block, it will be a mismatch.
         * @param idx An index such that 0 <= idx < getBlockLength()
         */
        public boolean isMatch( int idx )
        {
            boolean result = false;
            int len = mXSeq.length();
            if ( mYSeq.length() == len )
            {
                if ( idx < 0 || idx >= len )
                {
                    throw new IllegalArgumentException("index out of bounds");
                }
                BaseCall xCall = BaseCall.valueOf(mXSeq.charAt(idx));
                BaseCall yCall = BaseCall.valueOf(mYSeq.charAt(idx));
                result = BaseCall.intersectionOf(xCall,yCall) != BaseCall.X;
            }
            return result;
        }

        int getXOffset( int offset )
        {
            if ( mXSeq.length() == 0 )
            {
                offset = -1;
            }
            return mXSeq.getStart() + offset;
        }

        int getYOffset( int offset )
        {
            if ( mYSeq.length() == 0 )
            {
                offset = -1;
            }
            return mYSeq.getStart() + offset;
        }

        char getATrackChar( int offset, String symbols )
        {
            char result = 0;
            switch ( getType() )
            {
              case PAIRWISE:
                BaseCall xBase = BaseCall.valueOf(mXSeq.charAt(offset));
                BaseCall yBase = BaseCall.valueOf(mYSeq.charAt(offset));
                result = symbols.charAt(MATCH_CHAR);
                if ( BaseCall.intersectionOf(xBase,yBase) == BaseCall.X )
                {
                    result = symbols.charAt(MISMATCH_CHAR);
                }
                break;
              case X_GAP:
                result = symbols.charAt(GAP_CHAR);
                if ( !mXSeq.hasPrefix() || !mXSeq.hasSuffix() )
                {
                    result = symbols.charAt(END_GAP_CHAR);
                }
                break;
              case Y_GAP:
                result = symbols.charAt(GAP_CHAR);
                if ( !mYSeq.hasPrefix() || !mYSeq.hasSuffix() )
                {
                    result = symbols.charAt(END_GAP_CHAR);
                }
                break;
            }
            return result;
        }

        char getXTrackChar( int offset, String symbols )
        {
            char result;
            if ( mXSeq.length() != 0 )
            {
                result = mXSeq.charAt(offset);
            }
            else
            {
                result = symbols.charAt(GAP_CHAR);
                if ( !mXSeq.hasPrefix() || !mXSeq.hasSuffix() )
                {
                    result = symbols.charAt(END_GAP_CHAR);
                }
            }
            return result;
        }

        char getYTrackChar( int offset, String symbols )
        {
            char result;
            if ( mYSeq.length() != 0 )
            {
                result = mYSeq.charAt(offset);
            }
            else
            {
                result = symbols.charAt(GAP_CHAR);
                if ( !mYSeq.hasPrefix() || !mYSeq.hasSuffix() )
                {
                    result = symbols.charAt(END_GAP_CHAR);
                }
            }
            return result;
        }

        BaseCall getXCall( int offset )
        {
            BaseCall result = null;
            if ( mXSeq.length() != 0 )
            {
                result = BaseCall.valueOf(mXSeq.charAt(offset));
            }
            return result;
        }

        BaseCall getYCall( int offset )
        {
            BaseCall result = null;
            if ( mYSeq.length() != 0 )
            {
                result = BaseCall.valueOf(mYSeq.charAt(offset));
            }
            return result;
        }

        private static CharSequence getHyphens( int len )
        {
            while ( gHyphens.length() < len )
            {
                gHyphens = gHyphens + gHyphens;
            }
            return gHyphens.subSequence(0, len);
        }

        private CharSubSequence mXSeq;
        private CharSubSequence mYSeq;
        private int mNMatches;

        private static String gHyphens = "-------------------------";

        private static final int MATCH_CHAR = 0;
        private static final int MISMATCH_CHAR = 1;
        private static final int GAP_CHAR = 2;
        private static final int END_GAP_CHAR = 3;
    }

    public static class Lineup
    {
        Lineup( BaseCall xVal, BaseCall yVal )
        {
            mXVal = xVal;
            mYVal = yVal;
        }

        public BaseCall getXVal()
        {
            return mXVal;
        }

        public BaseCall getYVal()
        {
            return mYVal;
        }

        private BaseCall mXVal;
        private BaseCall mYVal;
    }

    public static class LineupIterator
        implements Iterator<Lineup>
    {
        public LineupIterator( Alignment alignment )
        {
            mBlocks = alignment.getBlocks();
            mCurBlock = mBlocks[0];
        }

        public boolean hasNext()
        {
            return mCurBlock != null;
        }

        public Lineup next()
        {
            Lineup result = new Lineup(mCurBlock.getXCall(mBaseIdx),mCurBlock.getYCall(mBaseIdx));
            if ( ++mBaseIdx >= mCurBlock.getBlockLength() )
            {
                mCurBlock = null;
                mBaseIdx = 0;
                if ( ++mBlockIdx < mBlocks.length )
                {
                    mCurBlock = mBlocks[mBlockIdx];
                }
            }
            return result;
        }

        public void remove()
        {
            throw new UnsupportedOperationException("can't remove lineups from an alignment");
        }

        private Block[] mBlocks;
        private int mBlockIdx;
        private Block mCurBlock;
        private int mBaseIdx;
    }

    private static class CharIterator
    {
        CharIterator( String symbols, List<Block> blocks )
        {
            mSymbols = symbols;
            mBlocks = blocks;
            mCurBlock = blocks.get(0);
        }

        boolean hasNext()
        {
            return mCurBlock != null;
        }

        char nextATrackChar()
        {
            char result = mCurBlock.getATrackChar(mBaseIndex,mSymbols);
            advance();
            return result;
        }

        char nextXTrackChar()
        {
            char result = mCurBlock.getXTrackChar(mBaseIndex,mSymbols);
            advance();
            return result;
        }

        char nextYTrackChar()
        {
            char result = mCurBlock.getYTrackChar(mBaseIndex,mSymbols);
            advance();
            return result;
        }

        int getXOffset()
        {
            return mXPos;
        }

        int getYOffset()
        {
            return mYPos;
        }

        private void advance()
        {
            mXPos = mCurBlock.getXOffset(mBaseIndex);
            mYPos = mCurBlock.getYOffset(mBaseIndex);
            if ( ++mBaseIndex >= mCurBlock.getBlockLength() )
            {
                mCurBlock = null;
                mBaseIndex = 0;
                if ( ++mBlockIndex < mBlocks.size() )
                {
                    mCurBlock = mBlocks.get(mBlockIndex);
                }
            }
        }
        private String mSymbols;
        private List<Block> mBlocks;
        private int mBlockIndex;
        private Block mCurBlock;
        private int mBaseIndex;
        private int mXPos;
        private int mYPos;
    }

    private interface LineSink
    {
        void write( String line );
    }

    private static class StringSink
        implements LineSink
    {
        public StringSink( int size )
        {
            mSB = new StringBuilder(size);
        }

        public void write( String line )
        {
            mSB.append(line);
        }

        @Override
        public String toString()
        {
            return mSB.toString();
        }

        private StringBuilder mSB;
    }

    private static class WriterSink
        implements LineSink
    {
        public WriterSink( Writer writer )
        {
            mWriter = writer;
        }

        public void write( String line )
        {
            if ( mException == null )
            {
                try
                {
                    mWriter.write(line);
                }
                catch ( IOException ioe )
                {
                    mException = ioe;
                }
            }
        }

        public IOException getException()
        {
            return mException;
        }

        private Writer mWriter;
        private IOException mException;
    }

    private static class PrintStreamSink
        implements LineSink
    {
        public PrintStreamSink( PrintStream ps )
        {
            mPS = ps;
        }

        public void write( String line )
        {
            mPS.print(line);
        }

        private PrintStream mPS;
    }
}