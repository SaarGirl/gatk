/*
 * $Id: SuffixTreeLeafNode.java 73684 2008-10-16 20:41:04Z tsharpe $
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A leaf node within a SuffixTree.  Used internally by SuffixTree.
 *
 * @author tsharpe
 * @version $Revision: 28397 $
 */
public class SuffixTreeLeafNode
    implements SuffixTreeNode
{
    public SuffixTreeLeafNode( CharSequence str, int labelStart )
    {
        mString = str;
        mLabelStart = labelStart;
    }

    public SuffixTreeLeafNode( CharSequence str, int labelStart, Object strings )
    {
        this(str,labelStart);
        mStrings = strings;
    }

    public CharSequence getString()
    {
        return mString;
    }

    public char charAt( int idx )
    {
        return mString.charAt(mLabelStart+idx);
    }

    public int getLabelStart()
    {
        return mLabelStart;
    }

    public int getLabelEnd()
    {
        return mString.length();
    }

    public int getLabelLength()
    {
        return mString.length() - mLabelStart;
    }

    public CharSequence getLabel()
    {
        return mString.subSequence(mLabelStart,getLabelEnd());
    }

    public SuffixTreeNode split( int idx )
    {
        SuffixTreeNode node = new SuffixTreeInternalNode(mString,mLabelStart,mLabelStart+idx);

        mLabelStart += idx;
        node.setChild(mString.charAt(mLabelStart),this);

        return node;
    }

    public SuffixTreeNode internalize( int labLen, CharSequence str, int labStart )
    {
        SuffixTreeNode node = new SuffixTreeInternalNode(mString,mLabelStart,mLabelStart+labLen,mStrings);

        mString = str;
        mLabelStart = labStart;
        mStrings = null;

        node.setChild(str.charAt(labStart),this);
        
        return node;
    }

    public SuffixTreeNode getChild( char chr )
    {
        return null;
    }

    public void setChild( char chr, SuffixTreeNode child )
    {
        throw new UnsupportedOperationException("A leaf node has no children.");
    }

    public SuffixTreeNode getSuffixLink()
    {
        return null;
    }

    public void setSuffixLink( SuffixTreeNode suffixLink )
    {
        throw new UnsupportedOperationException("A leaf node has no suffix link.");
    }

    public Iterator<CharSequence> getStringIterator()
    {
        Iterator<CharSequence> result;
        if ( mStrings == null || mStrings instanceof CharSequence )
        {
            result = new SingletonIterator<CharSequence>((CharSequence)mStrings);
        }
        else
        {
            result = downCastStringList().iterator();
        }
        return result;
    }

    public void addString( CharSequence str )
    {
        if ( mStrings == null )
        {
            mStrings = str;
        }
        else if ( mStrings instanceof List )
        {
            List<CharSequence> list = downCastStringList();
            if ( list.size() < MAX_LIST_LENGTH )
                list.add(str);
        }
        else
        {
            List<CharSequence> stringList = new ArrayList<CharSequence>(2);
            stringList.add((CharSequence)mStrings);
            stringList.add(str);
            mStrings = stringList;
        }
    }

    public boolean ubiquitousMatch()
    {
        return mStrings instanceof List && ((List<?>)mStrings).size() == MAX_LIST_LENGTH;
    }

    public boolean isRoot()
    {
        return false;
    }

    public boolean isLeaf()
    {
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<CharSequence> downCastStringList()
    {
        return (List<CharSequence>)mStrings;
    }

    private CharSequence mString;
    protected int mLabelStart;
    private Object mStrings;
    private static final int MAX_LIST_LENGTH = 5;
}