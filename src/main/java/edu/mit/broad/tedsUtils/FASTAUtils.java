/**
 * $Id: FASTAUtils.java 74625 2008-10-31 21:56:01Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package edu.mit.broad.tedsUtils;

/**
 * Utilities for dinking around with FASTA.
 *
 * @author tsharpe
 * @version $Revision$
 */
public class FASTAUtils
{
    /**
     * Remove anticipated types of garbage characters from sequence.
     * Removes:
     * Digits (often FASTA-like files will have an offset at the start or end of the line).
     * Whitespace (sometimes there's a space every 10 bases, and there may be ^M's and ^J's).
     * Returns null if it finds anything other than digits, whitespace, or unambiguous nucleotides.  
     * @param sequence Sequence to clean.
     * @return Cleaned sequence (or null if there's garbage).
     */
    public static String cleanSequence( String sequence )
    {
        if ( sequence == null )
            return null;

        int nnn = sequence.length();
        StringBuffer sb = new StringBuffer(nnn);
        for ( int idx = 0; idx < nnn; ++idx )
        {
            char chr = sequence.charAt(idx);
            if ( !Character.isDigit(chr) && !Character.isWhitespace(chr) )
            {
                switch ( (chr = Character.toUpperCase(chr)) )
                {
                  case 'A': case 'C': case 'G': case 'T':
                    sb.append(chr); break;
                  default:
                    return null;
                }
            }
        }
        return sb.toString();
    }

    /**
     * Reverse and complement a sequence.
     * Handles ambiguity codes. 
     * @param seq Sequence to reverse and complement.
     * @return The reverse-complemented sequence.
     */
    public static String reverseComplement( CharSequence seq )
    {
        int nnn = seq.length();
        StringBuilder sb = new StringBuilder(nnn);
        while ( nnn-- > 0 )
        {
            char chr = seq.charAt(nnn);
            int idx = gNucleotides.indexOf(chr);
            if ( idx == -1 )
                throw new IllegalArgumentException("Unrecognized character '" + chr + "'");
            sb.append( gComplementedNucleotides.charAt(idx) );
        }
        return sb.toString();
    }

    public static char complement( char code )
    {
        int idx = gNucleotides.indexOf(code);
        if ( idx == -1 )
            idx = 0; // return a '?'
        return gComplementedNucleotides.charAt(idx);
    }

    private static final String gNucleotides = "?ACGTBDHVRYKMSWNXacgtbdhvrykmswnx-";
    private static final String gComplementedNucleotides = "?TGCAVHDBYRMKSWNXtgcavhdbyrmkswnx-";
}