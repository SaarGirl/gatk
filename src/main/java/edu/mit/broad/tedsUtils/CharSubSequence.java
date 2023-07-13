/*
 * $Id: CharSubSequence.java 73459 2008-10-10 16:10:03Z tsharpe $
 * WHITEHEAD INSTITUTE
 * SOFTWARE COPYRIGHT NOTICE AGREEMENT
 * This software and its documentation are copyright 2002 by the
 * Whitehead Institute for Biomedical Research.  All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support
 * whatsoever.  The Whitehead Institute can not be responsible for its
 * use, misuse, or functionality.
 */
package edu.mit.broad.tedsUtils;

/**
 * A sub-sequence of a CharSequence that lets you recover the original CharSequence object.
 *
 * @author tsharpe
 * @version $Revision: 48897 $
 */
public class CharSubSequence
    implements CharSequence
{
    /**
     * Make one.
     * @param sequence The sequence.
     * @param start It's starting position.
     * @param end It's ending position.
     */
    public CharSubSequence( CharSequence sequence, int start, int end )
    {
        if ( sequence == null )
        {
            throw new IllegalArgumentException("CharSequence may not be null.");
        }
        int len = sequence.length();
        if ( start < 0 || start > len )
        {
            throw new IllegalArgumentException("Start is out of bounds.");
        }
        if ( end < start || end > len )
        {
            throw new IllegalArgumentException("End is out of bounds.");
        }
        if ( sequence instanceof CharSubSequence )
        {
            CharSubSequence ss = (CharSubSequence)sequence;
            sequence = ss.getOriginalSequence();
            start += ss.getStart();
            end += ss.getStart();
        }
        mSequence = sequence;
        mStart = start;
        mLength = end - start;
    }

    /**
     * The length of the subsequence.
     */
    public int length()
    {
        return mLength;
    }

    /**
     * Character at a specified position of the subsequence.
     * Equivalent to getOriginalSequence().charAt(getStart()+index)
     */
    public char charAt( int index )
    {
        if ( index < 0 || index >= mLength )
        {
            throw new IllegalArgumentException("Index (" + index + ") is out of bounds (" + mLength + ").");
        }

        return mSequence.charAt(mStart+index);
    }

    /**
     * Returns a subsequence on the original sequence that represents
     * the specified portion of this subsequence.
     */
    public CharSubSequence subSequence( int start, int end )
    {
        return new CharSubSequence(mSequence,mStart+start,mStart+end);
    }

    /**
     * The original, underlying sequence.
     */
    public CharSequence getOriginalSequence()
    {
        return mSequence;
    }

    /**
     * The starting position of this subsequence with respect to the original sequence.
     */
    public int getStart()
    {
        return mStart;
    }

    /**
     * The ending position of this subsequence with respect to the original sequence.
     */
    public int getEnd()
    {
        return mStart + mLength;
    }

    /**
     * Returns the part of the original sequence that precedes this subsequence.
     */
    public CharSubSequence getPrefix()
    {
        return new CharSubSequence(mSequence,0,mStart);
    }

    /**
     * Returns true if the prefix has a length > 0.
     */
    public boolean hasPrefix()
    {
        return mStart > 0;
    }

    /**
     * Returns the part of the original sequence that follows this subsequence.
     */
    public CharSubSequence getSuffix()
    {
        return new CharSubSequence(mSequence,mStart+mLength,mSequence.length());
    }

    /**
     * Returns true if the suffix has a length > 0.
     */
    public boolean hasSuffix()
    {
        return mStart+mLength < mSequence.length();
    }

    @Override
    public int hashCode()
    {
        int result = mHash;

        if ( result == 0 )
        {
            int idx = mLength;
            while ( --idx >= 0 )
            {
                result = 47*result + charAt(idx);
            }

            if ( result == 0 )
                result = 0xdeadf00d;

            mHash = result;
        }

        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        boolean result = false;
        if ( this == obj )
        {
            result = true;
        }
        else if ( obj instanceof CharSubSequence )
        {
            result = equals(this,(CharSequence)obj);
        }
        return result;
    }

    @Override
    public String toString()
    {
        return mSequence.toString().substring(mStart,mStart+mLength);
    }

    /**
     * Compare two CharSequences.
     */
    public static boolean equals( CharSequence seq1, CharSequence seq2 )
    {
        if ( seq1 == null )
            throw new IllegalArgumentException("seq1 cannot be null");
        if ( seq2 == null )
            throw new IllegalArgumentException("seq2 cannot be null");

        boolean result = true;
        int idx = seq1.length();
        if ( idx != seq2.length() )
        {
            result = false;
        }
        else
        {
            while ( --idx >= 0 )
            {
                if ( seq1.charAt(idx) != seq2.charAt(idx) )
                {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    private CharSequence mSequence;
    private int mStart;
    private int mLength;
    private int mHash;
}