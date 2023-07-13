/*
 * $Id: SuffixTreeMatchListener.java 73459 2008-10-10 16:10:03Z tsharpe $
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
 * Something that cares about suffix tree matches.
 * The listener gets called while walking the tree on a probe sequence.
 *
 * @author tsharpe
 * @version $Revision: 13394 $
 */
public interface SuffixTreeMatchListener
{
    /**
     * Signals a match event on some node of the tree.
     * You will get one such call for each suffix of the probe string.
     * You can iterate over the strings in that node and its children to
     * find the set of substrings in the SuffixTree that match this maximum-length
     * matching prefix of the probe.  Parent nodes may contain shorter suffix matches
     * on other strings, but there's currently no good way of accessing them.
     *
     * @param probe The CharSequence you passed to the SuffixTree's match method.
     * @param begin The starting offset of the match.
     * @param end The ending offset of the match.  (I.e. first mismatch offset, or probe length.)
     * @param pos A position in the SuffixTree.
     * @return True, if you'd like the iteration to continue.
     */
    public boolean match( CharSequence probe, int begin, int end, SuffixTreePosition pos );
}