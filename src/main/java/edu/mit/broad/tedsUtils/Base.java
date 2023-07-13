/*
 * $Id: Base.java 71126 2008-08-07 16:05:20Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2008 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */
package edu.mit.broad.tedsUtils;

/**
 * The four bases in DNA.
 *
 * @author tsharpe
 * @version $Revision: 44052 $
 */
public enum Base
{
    A("adenine",RingType.Purine,2),
    C("cytosine",RingType.Pyrimidine,3),
    G("guanine",RingType.Purine,3),
    T("thymine",RingType.Pyrimidine,2);

    Base( String fullName, RingType ringType, int nHBonds )
    {
        mFullName = fullName;
        mRingType = ringType;
        mNHBonds = nHBonds;
    }

    public Base complement()
    {
        return mComplement;
    }

    /**
     * Returns the full chemical name.
     */
    public String fullName()
    {
        return mFullName;
    }

    /**
     * Type of ring.
     */
    public RingType ringType()
    {
        return mRingType;
    }

    /**
     * Number of Hydrogen bonds it makes to its complement.
     */
    public int nHBonds()
    {
        return mNHBonds;
    }

    /**
     * Returns the character code for the base.
     * (It's just the first letter of the name.)
     */
    public char code()
    {
        return name().charAt(0);
    }

    /**
     * Returns the Base corresponding to a specified character code.
     * Returns null if there is no Base corresponding to the name.
     * @param code The code.
     * @return The Base (or null).
     */
    public static Base valueOf( char code )
    {
        Base result = null;
        switch ( Character.toUpperCase(code) )
        {
        case 'A':
            result = A;
            break;
        case 'C':
            result = C;
            break;
        case 'G':
            result = G;
            break;
        case 'T':
            result = T;
            break;
        }
        return result;
    }

    static
    {
        A.mComplement = T;
        C.mComplement = G;
        G.mComplement = C;
        T.mComplement = A;
    }

    private String mFullName;
    private RingType mRingType;
    private int mNHBonds;
    private Base mComplement;
}