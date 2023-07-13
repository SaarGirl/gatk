/*
 * $Id: IntArrayList.java 70611 2008-07-29 18:06:55Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */
package edu.mit.broad.tedsUtils;


/**
 * A series of ints supported by an array.
 *
 * @author tsharpe
 * @version $Revision: 70611 $
 */
public class IntArrayList
    implements NumberList
{
    public IntArrayList()
    {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public IntArrayList( int capacity )
    {
        mVals = new int[capacity];
    }

    public IntArrayList( int[] vals )
    {
        mVals = vals == null ? NULL_LIST : vals;
        mSize = mVals.length;
    }

    public int size()
    {
        return mSize;
    }

    public int intVal( int idx )
    {
        return mVals[idx];
    }

    public double dblVal( int idx )
    {
        return intVal(idx);
    }

    public synchronized void add( int val )
    {
        if ( mSize >= mVals.length )
        {
            int[] newVals = new int[mVals.length*2 + 5];
            System.arraycopy(mVals,0,newVals,0,mVals.length);
            mVals = newVals;
        }
        mVals[mSize++] = val;
    }

    public Iterator iterator()
    {
        return new NumberListUtil.Itr(this);
    }

    private int[] mVals;
    private int mSize;
    private static final int[] NULL_LIST = new int[0];
    private static final int DEFAULT_INITIAL_CAPACITY = 15;
}