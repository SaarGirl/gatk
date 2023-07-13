/**
 * $Id: NybbleDiagMatrix.java 72446 2008-09-10 22:29:03Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package edu.mit.broad.tedsUtils.align;

/**
 * A two-dimensional lower-diagonal square matrix of nybbles.  Boundaries are checked
 * only by assertions, so don't count on run-time exceptions to save you if you call
 * get or set with bogus indices.
 *
 * @author tsharpe
 * @version $Revision$
 */
public class NybbleDiagMatrix
{
    /**
     * Make one.
     * @param size Size of full square.
     */
    public NybbleDiagMatrix( int size )
    {
        if ( size < 1 )
        {
            throw new IllegalArgumentException("size must be positive.");
        }
        mSize = size;
        mVals = new byte[size*(size-1)/2];
    }

    /**
     * Get the nybble at the specified location.
     * @param col 0 <= col < row
     * @param row 1 <= row < size
     */
    public int get( int col, int row )
    {
        assert( col >= 0 && col < row && row > 0 && row < mSize );

        int idx = row*(row-1)/2 + col;
        int val = mVals[idx/2];
        if ( (idx & 1) == 1 )
        {
            val >>= 4;
        }
        return val & 0x0F;
    }

    /**
     * Set the nybble at the specified location.
     * @param col 0 <= col < row
     * @param row 1 <= row < size
     * @param val 0 <= val < 16
     */
    public void set( int col, int row, int val )
    {
        assert( col >= 0 && col < row && row > 0 && row < mSize );
        assert( val >= 0 && val < 16 );

        int idx = row*(row-1)/2 + col;
        int offset = idx/2;
        int oldVal = mVals[offset];
        if ( (idx & 1) == 1 )
        {
            mVals[offset] = (byte)((oldVal & 0x0F) | (val << 4));
        }
        else
        {
            mVals[offset] = (byte)((oldVal & 0xF0) | (val & 0x0F));
        }
    }

    private int mSize;
    private byte[] mVals;
}