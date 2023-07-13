/**
 * $Id: SampleDataSeries.java 71126 2008-08-07 16:05:20Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package edu.mit.broad.tedsUtils;

import java.util.List;

import edu.mit.broad.tedsUtils.Reading.Sample;


/**
 * A comment needs to be added.
 *
 * @author tsharpe
 * @version $Revision: 71126 $
 */
public class SampleDataSeries
    implements NumberList
{
    public SampleDataSeries( List<Sample> sampleList, Base channel )
    {
        if ( sampleList == null )
        {
            throw new IllegalArgumentException("Sample list cannot be null.");
        }
        if ( channel == null )
        {
            throw new IllegalArgumentException("Channel cannot be null.");
        }
        mSampleList = sampleList;
        mChannel = channel;
    }

    public int size()
    {
        return mSampleList.size();
    }

    public int intVal( int idx )
    {
        return Math.round((float)dblVal(idx));
    }

    public double dblVal( int idx )
    {
        return mSampleList.get(idx).getIntensity(mChannel);
    }

    public Iterator iterator()
    {
        return new Itr(mSampleList.iterator(),mChannel);
    }

    private List<Sample> mSampleList;
    private Base mChannel;

    private static class Itr
        implements Iterator
    {
        public Itr( java.util.Iterator<Sample> itr, Base channel )
        {
            mItr = itr;
            mChannel = channel;
        }

        public boolean hasNext()
        {
            return mItr.hasNext();
        }

        public int nextInt()
        {
            return Math.round((float)nextDbl());
        }

        public double nextDbl()
        {
            return mItr.next().getIntensity(mChannel);
        }

        private java.util.Iterator<Sample> mItr;
        private Base mChannel;
    }
}