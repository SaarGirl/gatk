/*
 * $Id: SuffixTreeNode.java 73459 2008-10-10 16:10:03Z tsharpe $
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

import java.util.Iterator;

/**
 * A node within a suffix tree.
 * It knows about the label on the edge that leads to it, and may have up to four children.
 *
 * @author tsharpe
 * @version $Revision: 28397 $
 */
public interface SuffixTreeNode
{
    CharSequence getString();

    char charAt( int idx );

    int getLabelStart();

    int getLabelEnd();
    int getLabelLength();

    CharSequence getLabel();

    SuffixTreeNode split( int idx );
    SuffixTreeNode internalize( int labLen, CharSequence str, int labStart );

    SuffixTreeNode getChild( char chr );
    void setChild( char chr, SuffixTreeNode child );

    SuffixTreeNode getSuffixLink();
    void setSuffixLink( SuffixTreeNode suffixLink );

    Iterator<CharSequence> getStringIterator();
    void addString( CharSequence str );
    boolean ubiquitousMatch();

    boolean isRoot();
    boolean isLeaf();
}