/*
 * $Id: IntervalTree.java 100381 2009-06-02 20:39:33Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */
package edu.mit.broad.tedsUtils;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A Red-Black tree with intervals for keys.
 * Intervals are kept in sorted order first by start value and then by end value. This cannot be overridden.
 * Not thread-safe, and cannot be made so.  You must synchronize externally.
 *
 * There's some weird stuff about sentinel values for the put and remove methods.  Here's what's up with that:
 * When you update the value associated with some interval by doing a put with an interval that's already in the
 * tree, the old value is returned to you.  But maybe you've put some nulls into the tree as values.  (That's legal.)
 * In that case, when you get a null value returned by put you can't tell whether the interval was inserted into the tree
 * and there was no old value to return to you or whether you just updated an existing interval that had a null value
 * associated with it.  (Both situations return null.)  IF you're inserting nulls as values, and IF you need to be able
 * to tell whether the put operation did an insert or an update, you can do a special thing so that you can distinguish
 * these cases:  set the sentinel value for the tree to some singleton object that you never ever use as a legitimate
 * value.  Then when you call put you'll get your sentinel value back for an insert, but you'll get null back for an
 * update of a formerly-null value.  Same thing happens for remove:  set the sentinel IF you've used nulls for values,
 * and IF you need to be able to tell the difference between remove not finding the interval and remove removing an
 * interval that has a null value associated with it.
 * If you're not using nulls as values, or if you don't care to disambiguate these cases, then just forget about
 * all this weirdness.  The sentinel value is null by default, so put and remove will behave like you might expect them
 * to if you're not worrying about this stuff:  they'll return null for novel insertions and failed deletions.
 *
 * @author tsharpe
 * @version $Revision: 100381 $
 */
public class IntervalTree<V>
{
    /**
     * Return the number of intervals in the tree.
     * @return The number of intervals.
     */
    public int size()
    {
        return mRoot == null ? 0 : mRoot.getSize();
    }

    /**
     * Remove all entries.
     */
    public void clear()
    {
        mRoot = null;
    }

    /**
     * Put a new interval into the tree (or update the value associated with an existing interval).
     * If the interval is novel, the special sentinel value (which is null by default) is returned.
     * @param interval The interval.
     * @param value The associated value.
     * @return The old value associated with that interval, or the sentinel value.
     */
    public V put( Interval interval, V value )
    {
        return put(interval.getStart(),interval.getEnd(),value);
    }

    /**
     * Put a new interval into the tree (or update the value associated with an existing interval).
     * If the interval is novel, the special sentinel value (which is null by default) is returned.
     * @param start The interval's start.
     * @param end The interval's end.
     * @param value The associated value.
     * @return The old value associated with that interval, or the sentinel value.
     */
    @SuppressWarnings("null")
    public V put( int start, int end, V value )
    {
        if ( start > end )
            throw new IllegalArgumentException("Start cannot exceed end.");

        V result = mSentinel;

        if ( mRoot == null )
        {
            mRoot = new Node<V>(start,end,value);
        }
        else
        {
            Node<V> parent = null;
            Node<V> node = mRoot;
            int cmpVal = 0;

            while ( node != null )
            {
                parent = node; // last non-null node
                cmpVal = node.compare(start,end);
                if ( cmpVal == 0 )
                {
                    break;
                }

                node = cmpVal < 0 ? node.getLeft() : node.getRight();
            }

            if ( cmpVal == 0 )
            {
                result = parent.setValue(value);
            }
            else
            {
                if ( cmpVal < 0 )
                {
                    mRoot = parent.insertLeft(start,end,value,mRoot);
                }
                else
                {
                    mRoot = parent.insertRight(start,end,value,mRoot);
                }
            }
        }

        return result;
    }

    /**
     * Remove an interval from the tree.
     * If the interval is not found, the special sentinel value (which is null by default) is returned.
     * @param interval The interval to remove.
     * @return The value associated with the deleted interval, or the sentinel value.
     */
    public V remove( Interval interval )
    {
        return remove(interval.getStart(),interval.getEnd());
    }

    /**
     * Remove an interval from the tree.
     * If the interval is not found, the special sentinel value (which is null by default) is returned.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return The value associated with the deleted interval, or the sentinel value.
     */
    public V remove( int start, int end )
    {
        V result = mSentinel;
        Node<V> node = mRoot;

        while ( node != null )
        {
            int cmpVal = node.compare(start,end);
            if ( cmpVal == 0 )
            {
                result = node.getValue();
                mRoot = node.remove(mRoot);
                break;
            }

            node = cmpVal < 0 ? node.getLeft() : node.getRight();
        }

        return result;
    }

    /**
     * Find an interval.
     * @param interval The interval sought.
     * @return The Node that represents that interval, or null.
     */
    public Node<V> find( Interval interval )
    {
        return find(interval.getStart(),interval.getEnd());
    }

    /**
     * Find an interval.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return The Node that represents that interval, or null.
     */
    public Node<V> find( int start, int end )
    {
        Node<V> node = mRoot;

        while ( node != null )
        {
            int cmpVal = node.compare(start,end);
            if ( cmpVal == 0 )
            {
                break;
            }

            node = cmpVal < 0 ? node.getLeft() : node.getRight();
        }

        return node;
    }

    /**
     * Find the nth interval in the tree.
     * @param idx The rank of the interval sought (from 0 to size()-1).
     * @return The Node that represents the nth interval.
     */
    public Node<V> findByIndex( int idx )
    {
        return Node.findByRank(mRoot,idx+1);
    }

    /**
     * Find the rank of the specified interval.  If the specified interval is not in the
     * tree, then -1 is returned.
     * @param interval The interval for which the index is sought.
     * @return The rank of that interval, or -1.
     */
    public int getIndex( Interval interval )
    {
        return getIndex(interval.getStart(),interval.getEnd());
    }

    /**
     * Find the rank of the specified interval.  If the specified interval is not in the
     * tree, then -1 is returned.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return The rank of that interval, or -1.
     */
    public int getIndex( int start, int end )
    {
        return Node.getRank(mRoot,start,end) - 1;
    }

    /**
     * Find the least interval in the tree.
     * @return The earliest interval, or null if the tree is empty.
     */
    public Node<V> min()
    {
        Node<V> result = null;
        Node<V> node = mRoot;

        while ( node != null )
        {
            result = node;
            node = node.getLeft();
        }

        return result;
    }

    /**
     * Find the earliest interval in the tree greater than or equal to the specified interval.
     * @param interval The interval sought. 
     * @return The earliest >= interval, or null if there is none.
     */
    public Node<V> min( Interval interval )
    {
        return min(interval.getStart(),interval.getEnd());
    }

    /**
     * Find the earliest interval in the tree greater than or equal to the specified interval.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return The earliest >= interval, or null if there is none.
     */
    @SuppressWarnings("null")
    public Node<V> min( int start, int end )
    {
        Node<V> result = null;
        Node<V> node = mRoot;
        int cmpVal = 0;

        while ( node != null )
        {
            result = node;
            cmpVal = node.compare(start,end);
            if ( cmpVal == 0 )
            {
                break;
            }

            node = cmpVal < 0 ? node.getLeft() : node.getRight();
        }

        if ( cmpVal > 0 )
        {
            result = result.getNext();
        }

        return result;
    }

    /**
     * Find the earliest interval in the tree that overlaps the specified interval.
     * @param interval The interval sought. 
     * @return The earliest overlapping interval, or null if there is none.
     */
    public Node<V> minOverlapper( Interval interval )
    {
        return minOverlapper(interval.getStart(),interval.getEnd());
    }

    /**
     * Find the earliest interval in the tree that overlaps the specified interval.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return The earliest overlapping interval, or null if there is none.
     */
    public Node<V> minOverlapper( int start, int end )
    {
        Node<V> result = null;
        Node<V> node = mRoot;

        if ( node != null && node.getMaxEnd() > start )
        {
            while ( true )
            {
                if ( node.getStart() < end && start < node.getEnd() )
                { // this node overlaps.  there might be a lesser overlapper down the left sub-tree.
                  // no need to consider the right sub-tree:  even if there's an overlapper, if won't be minimal
                    result = node;
                    node = node.getLeft();
                    if ( node == null || node.getMaxEnd() <= start )
                        break; // no left sub-tree or all nodes end too early
                }
                else
                { // no overlap.  if there might be a left sub-tree overlapper, consider the left sub-tree.
                    Node<V> left = node.getLeft();
                    if ( left != null && left.getMaxEnd() > start )
                    {
                        node = left;
                    }
                    else
                    { // left sub-tree cannot contain an overlapper.  consider the right sub-tree.
                        if ( node.getStart() >= end )
                            break; // everything in the right sub-tree is past the end of the query interval

                        node = node.getRight();
                        if ( node == null || node.getMaxEnd() <= start )
                            break; // no right sub-tree or all nodes end too early
                    }
                }
            }
        }

        return result;
    }

    /**
     * Find the greatest interval in the tree.
     * @return The latest interval, or null if the tree is empty.
     */
    public Node<V> max()
    {
        Node<V> result = null;
        Node<V> node = mRoot;

        while ( node != null )
        {
            result = node;
            node = node.getRight();
        }

        return result;
    }

    /**
     * Find the latest interval in the tree less than or equal to the specified interval.
     * @param interval The interval sought. 
     * @return The latest <= interval, or null if there is none.
     */
    public Node<V> max( Interval interval )
    {
        return max(interval.getStart(),interval.getEnd());
    }

    /**
     * Find the latest interval in the tree less than or equal to the specified interval.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return The latest >= interval, or null if there is none.
     */
    @SuppressWarnings("null")
    public Node<V> max( int start, int end )
    {
        Node<V> result = null;
        Node<V> node = mRoot;
        int cmpVal = 0;

        while ( node != null )
        {
            result = node;
            cmpVal = node.compare(start,end);
            if ( cmpVal == 0 )
            {
                break;
            }

            node = cmpVal < 0 ? node.getLeft() : node.getRight();
        }

        if ( cmpVal < 0 )
        {
            result = result.getPrev();
        }

        return result;
    }

    /**
     * Return an iterator over the entire tree.
     * @return An iterator.
     */
    public Iterator<Node<V>> iterator()
    {
        return new FwdIterator(min());
    }

    /**
     * Return an iterator over all intervals greater than or equal to the specified interval.
     * @param interval The minimum interval.
     * @return An iterator.
     */
    public Iterator<Node<V>> iterator( Interval interval )
    {
        return new FwdIterator(min(interval.getStart(),interval.getEnd()));
    }

    /**
     * Return an iterator over all intervals greater than or equal to the specified interval.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return An iterator.
     */
    public Iterator<Node<V>> iterator( int start, int end )
    {
        return new FwdIterator(min(start,end));
    }

    /**
     * Return an iterator over all intervals overlapping the specified range.
     * @param start The range start.
     * @param end The range end.
     * @return An iterator.
     */
    public Iterator<Node<V>> overlappers( int start, int end )
    {
        return new OverlapIterator(start,end);
    }

    /**
     * Return an iterator over the entire tree that returns intervals in reverse order.
     * @return An iterator.
     */
    public Iterator<Node<V>> reverseIterator()
    {
        return new RevIterator(max());
    }

    /**
     * Return an iterator over all intervals less than or equal to the specified interval, in reverse order.
     * @param interval The maximum interval.
     * @return An iterator.
     */
    public Iterator<Node<V>> reverseIterator( Interval interval )
    {
        return new RevIterator(max(interval.getStart(),interval.getEnd()));
    }

    /**
     * Return an iterator over all intervals less than or equal to the specified interval, in reverse order.
     * @param start The interval's start.
     * @param end The interval's end.
     * @return An iterator.
     */
    public Iterator<Node<V>> reverseIterator( int start, int end )
    {
        return new RevIterator(max(start,end));
    }

    /**
     * Get the special sentinel value that will be used to signal novelty when putting a new interval
     * into the tree, or to signal "not found" when removing an interval.  This is null by default.
     * @return The sentinel value.
     */
    public V getSentinel()
    {
        return mSentinel;
    }

    /**
     * Set the special sentinel value that will be used to signal novelty when putting a new interval
     * into the tree, or to signal "not found" when removing an interval.
     * @param sentinel The new sentinel value.
     * @return The old sentinel value.
     */
    public V setSentinel( V sentinel )
    {
        V result = mSentinel;
        mSentinel = sentinel;
        return result;
    }

    void removeNode( Node<V> node )
    {
        mRoot = node.remove(mRoot);
    }

    private Node<V> mRoot;
    private V mSentinel;

    public static class Node<V1>
        extends Interval.Impl
    {
        Node( int start, int end, V1 value )
        {
            super(start,end);
            mValue = value;
            mSize = 1;
            mMaxEnd = getEnd();
            mIsBlack = true;
        }

        Node( Node<V1> parent, int start, int end, V1 value )
        {
            super(start,end);
            mParent = parent;
            mValue = value;
            mMaxEnd = getEnd();
            mSize = 1;
        }

        public V1 getValue()
        {
            return mValue;
        }

        public V1 setValue( V1 value )
        {
            V1 result = mValue;
            mValue = value;
            return result;
        }

        int getSize()
        {
            return mSize;
        }

        int getMaxEnd()
        {
            return mMaxEnd;
        }

        Node<V1> getLeft()
        {
            return mLeft;
        }

        Node<V1> insertLeft( int start, int end, V1 value, Node<V1> root )
        {
            mLeft = new Node<V1>(this,start,end,value);
            return insertFixup(mLeft,root);
        }

        Node<V1> getRight()
        {
            return mRight;
        }

        Node<V1> insertRight( int start, int end, V1 value, Node<V1> root )
        {
            mRight = new Node<V1>(this,start,end,value);
            return insertFixup(mRight,root);
        }

        Node<V1> getNext()
        {
            Node<V1> result;

            if ( mRight != null )
            {
                result = mRight;
                while ( result.mLeft != null )
                {
                    result = result.mLeft;
                }
            }
            else
            {
                Node<V1> node = this;
                result = mParent;
                while ( result != null && node == result.mRight )
                {
                    node = result;
                    result = result.mParent;
                }
            }

            return result;
        }

        Node<V1> getPrev()
        {
            Node<V1> result;

            if ( mLeft != null )
            {
                result = mLeft;
                while ( result.mRight != null )
                {
                    result = result.mRight;
                }
            }
            else
            {
                Node<V1> node = this;
                result = mParent;
                while ( result != null && node == result.mLeft )
                {
                    node = result;
                    result = result.mParent;
                }
            }

            return result;
        }

        boolean wasRemoved()
        {
            return mSize == 0;
        }

        Node<V1> remove( Node<V1> root )
        {
            if ( mSize == 0 )
            {
                throw new IllegalStateException("Entry was already removed.");
            }

            if ( mLeft == null )
            {
                if ( mRight == null )
                { // no children
                    if ( mParent == null )
                    {
                        root = null;
                    }
                    else if ( mParent.mLeft == this )
                    {
                        mParent.mLeft = null;
                        fixup(mParent);

                        if ( mIsBlack )
                            root = removeFixup(mParent,null,root);
                    }
                    else
                    {
                        mParent.mRight = null;
                        fixup(mParent);

                        if ( mIsBlack )
                            root = removeFixup(mParent,null,root);
                    }
                }
                else
                { // single child on right
                    root = spliceOut(mRight,root);
                }
            }
            else if ( mRight == null )
            { // single child on left
                root = spliceOut(mLeft,root);
            }
            else
            { // two children
                Node<V1> next = getNext();
                root = next.remove(root);

                // put next into tree in same position as this, effectively removing this
                if ( (next.mParent = mParent) == null )
                    root = next;
                else if ( mParent.mLeft == this )
                    mParent.mLeft = next;
                else
                    mParent.mRight = next;

                if ( (next.mLeft = mLeft) != null )
                {
                    mLeft.mParent = next;
                }

                if ( (next.mRight = mRight) != null )
                {
                    mRight.mParent = next;
                }

                next.mIsBlack = mIsBlack;
                next.mSize = mSize;
                fixup(next);
            }

            mSize = 0;
            return root;
        }

        // backwards comparison!  compares start+end to this.
        int compare( int start, int end )
        {
            int result = 0;

            if ( start > getStart() )
                result = 1;
            else if ( start < getStart() )
                result = -1;
            else if ( end > getEnd() )
                result = 1;
            else if ( end < getEnd() )
                result = -1;

            return result;
        }

        static <V1> Node<V1> getNextOverlapper( Node<V1> node, int start, int end )
        {
            do
            {
                Node<V1> nextNode = node.mRight;
                if ( nextNode != null && nextNode.mMaxEnd > start )
                {
                    node = nextNode;
                    while ( (nextNode = node.mLeft) != null && nextNode.mMaxEnd > start )
                        node = nextNode;
                }
                else
                {
                    nextNode = node;
                    while ( (node = nextNode.mParent) != null && node.mRight == nextNode )
                        nextNode = node;
                }

                if ( node != null && node.getStart() >= end )
                    node = null;
            }
            while ( node != null && !(node.getStart() < end && start < node.getEnd()) );

            return node;
        }

        static <V1> Node<V1> findByRank( Node<V1> node, int rank )
        {
            while ( node != null )
            {
                int nodeRank = node.getRank();
                if ( rank == nodeRank )
                    break;

                if ( rank < nodeRank )
                {
                    node = node.mLeft;
                }
                else
                {
                    node = node.mRight;
                    rank -= nodeRank;
                }
            }

            return node;
        }

        static <V1> int getRank( Node<V1> node, int start, int end )
        {
            int rank = 0;

            while ( node != null )
            {
                int cmpVal = node.compare(start,end);
                if ( cmpVal < 0 )
                {
                    node = node.mLeft;
                }
                else
                {
                    rank += node.getRank();
                    if ( cmpVal == 0 )
                        return rank; // EARLY RETURN!!!

                    node = node.mRight;
                }
            }

            return 0;
        }

        private int getRank()
        {
            int result = 1;
            if ( mLeft != null )
                result = mLeft.mSize + 1;
            return result;
        }

        private Node<V1> spliceOut( Node<V1> child, Node<V1> root )
        {
            if ( (child.mParent = mParent) == null )
            {
                root = child;
                child.mIsBlack = true;
            }
            else
            {
                if ( mParent.mLeft == this )
                    mParent.mLeft = child;
                else
                    mParent.mRight = child;
                fixup(mParent);

                if ( mIsBlack )
                    root = removeFixup(mParent,child,root);
            }

            return root;
        }

        private Node<V1> rotateLeft( Node<V1> root )
        {
            Node<V1> child = mRight;

            int childSize = child.mSize;
            child.mSize = mSize;
            mSize -= childSize;

            if ( (mRight = child.mLeft) != null )
            {
                mRight.mParent = this;
                mSize += mRight.mSize;
            }

            if ( (child.mParent = mParent) == null )
                root = child;
            else if ( this == mParent.mLeft )
                mParent.mLeft = child;
            else
                mParent.mRight = child;

            child.mLeft = this;
            mParent = child;

            setMaxEnd();
            child.setMaxEnd();

            return root;
        }

        private Node<V1> rotateRight( Node<V1> root )
        {
            Node<V1> child = mLeft;

            int childSize = child.mSize;
            child.mSize = mSize;
            mSize -= childSize;

            if ( (mLeft = child.mRight) != null )
            {
                mLeft.mParent = this;
                mSize += mLeft.mSize;
            }

            if ( (child.mParent = mParent) == null )
                root = child;
            else if ( this == mParent.mLeft )
                mParent.mLeft = child;
            else
                mParent.mRight = child;

            child.mRight = this;
            mParent = child;

            setMaxEnd();
            child.setMaxEnd();

            return root;
        }

        private void setMaxEnd()
        {
            mMaxEnd = getEnd();
            if ( mLeft != null )
                mMaxEnd = Math.max(mMaxEnd,mLeft.mMaxEnd);
            if ( mRight != null )
                mMaxEnd = Math.max(mMaxEnd,mRight.mMaxEnd);
        }

        private static <V1> void fixup( Node<V1> node )
        {
            do
            {
                node.mSize = 1;
                node.mMaxEnd = node.getEnd();
                if ( node.mLeft != null )
                {
                    node.mSize += node.mLeft.mSize;
                    node.mMaxEnd = Math.max(node.mMaxEnd,node.mLeft.mMaxEnd);
                }
                if ( node.mRight != null )
                {
                    node.mSize += node.mRight.mSize;
                    node.mMaxEnd = Math.max(node.mMaxEnd,node.mRight.mMaxEnd);
                }
            }
            while ( (node = node.mParent) != null );
        }

        private static <V1> Node<V1> insertFixup( Node<V1> daughter, Node<V1> root )
        {
            Node<V1> mom = daughter.mParent;
            fixup(mom);

            while( mom != null && !mom.mIsBlack )
            {
                Node<V1> gramma = mom.mParent;
                Node<V1> auntie = gramma.mLeft;
                if ( auntie == mom )
                {
                    auntie = gramma.mRight;
                    if ( auntie != null && !auntie.mIsBlack )
                    {
                        mom.mIsBlack = true;
                        auntie.mIsBlack = true;
                        gramma.mIsBlack = false;
                        daughter = gramma;
                    }
                    else
                    {
                        if ( daughter == mom.mRight )
                        {
                            root = mom.rotateLeft(root);
                            mom = daughter;
                        }
                        mom.mIsBlack = true;
                        gramma.mIsBlack = false;
                        root = gramma.rotateRight(root);
                        break;
                    }
                }
                else
                {
                    if ( auntie != null && !auntie.mIsBlack )
                    {
                        mom.mIsBlack = true;
                        auntie.mIsBlack = true;
                        gramma.mIsBlack = false;
                        daughter = gramma;
                    }
                    else
                    {
                        if ( daughter == mom.mLeft )
                        {
                            root = mom.rotateRight(root);
                            mom = daughter;
                        }
                        mom.mIsBlack = true;
                        gramma.mIsBlack = false;
                        root = gramma.rotateLeft(root);
                        break;
                    }
                }
                mom = daughter.mParent;
            }
            root.mIsBlack = true;
            return root;
        }

        private static <V1> Node<V1> removeFixup( Node<V1> parent, Node<V1> node, Node<V1> root )
        {
            do
            {
                if ( node == parent.mLeft )
                {
                    Node<V1> sister = parent.mRight;
                    if ( !sister.mIsBlack )
                    {
                        sister.mIsBlack = true;
                        parent.mIsBlack = false;
                        root = parent.rotateLeft(root);
                        sister = parent.mRight;
                    }
                    if ( (sister.mLeft == null || sister.mLeft.mIsBlack) && (sister.mRight == null || sister.mRight.mIsBlack) )
                    {
                        sister.mIsBlack = false;
                        node = parent;
                    }
                    else
                    {
                        if ( sister.mRight == null || sister.mRight.mIsBlack )
                        {
                            sister.mLeft.mIsBlack = true;
                            sister.mIsBlack = false;
                            root = sister.rotateRight(root);
                            sister = parent.mRight;
                        }
                        sister.mIsBlack = parent.mIsBlack;
                        parent.mIsBlack = true;
                        sister.mRight.mIsBlack = true;
                        root = parent.rotateLeft(root);
                        node = root;
                    }
                }
                else
                {
                    Node<V1> sister = parent.mLeft;
                    if ( !sister.mIsBlack )
                    {
                        sister.mIsBlack = true;
                        parent.mIsBlack = false;
                        root = parent.rotateRight(root);
                        sister = parent.mLeft;
                    }
                    if ( (sister.mLeft == null || sister.mLeft.mIsBlack) && (sister.mRight == null || sister.mRight.mIsBlack) )
                    {
                        sister.mIsBlack = false;
                        node = parent;
                    }
                    else
                    {
                        if ( sister.mLeft == null || sister.mLeft.mIsBlack )
                        {
                            sister.mRight.mIsBlack = true;
                            sister.mIsBlack = false;
                            root = sister.rotateLeft(root);
                            sister = parent.mLeft;
                        }
                        sister.mIsBlack = parent.mIsBlack;
                        parent.mIsBlack = true;
                        sister.mLeft.mIsBlack = true;
                        root = parent.rotateRight(root);
                        node = root;
                    }
                }
                parent = node.mParent;
            }
            while ( parent != null && node.mIsBlack );

            node.mIsBlack = true;
            return root;
        }

        private Node<V1> mParent;
        private Node<V1> mLeft;
        private Node<V1> mRight;
        private V1 mValue;
        private int mSize;
        private int mMaxEnd;
        private boolean mIsBlack;
    }

    public class FwdIterator
        implements Iterator<Node<V>>
    {
        public FwdIterator( Node<V> node )
        {
            mNext = node;
        }

        public boolean hasNext()
        {
            return mNext != null;
        }

        public Node<V> next()
        {
            if ( mNext == null )
            {
                throw new NoSuchElementException("No next element.");
            }

            if ( mNext.wasRemoved() )
            {
                mNext = min(mNext.getStart(),mNext.getEnd());
                if ( mNext == null )
                    throw new ConcurrentModificationException("Current element was removed, and there are no more elements.");
            }
            mLast = mNext;
            mNext = mNext.getNext();
            return mLast;
        }

        public void remove()
        {
            if ( mLast == null )
            {
                throw new IllegalStateException("No entry to remove.");
            }

            removeNode(mLast);
            mLast = null;
        }

        private Node<V> mNext;
        private Node<V> mLast;
    }

    public class RevIterator
        implements Iterator<Node<V>>
    {
        public RevIterator( Node<V> node )
        {
            mNext = node;
        }

        public boolean hasNext()
        {
            return mNext != null;
        }

        public Node<V> next()
        {
            if ( mNext == null )
                throw new NoSuchElementException("No next element.");
            if ( mNext.wasRemoved() )
            {
                mNext = max(mNext.getStart(),mNext.getEnd());
                if ( mNext == null )
                    throw new ConcurrentModificationException("Current element was removed, and there are no more elements.");
            }
            mLast = mNext;
            mNext = mNext.getPrev();
            return mLast;
        }

        public void remove()
        {
            if ( mLast == null )
            {
                throw new IllegalStateException("No entry to remove.");
            }

            removeNode(mLast);
            mLast = null;
        }

        private Node<V> mNext;
        private Node<V> mLast;
    }

    public class OverlapIterator
        implements Iterator<Node<V>>
    {
        public OverlapIterator( int start, int end )
        {
            mNext = minOverlapper(start,end);
            mStart = start;
            mEnd = end;
        }

        public boolean hasNext()
        {
            return mNext != null;
        }

        public Node<V> next()
        {
            if ( mNext == null )
            {
                throw new NoSuchElementException("No next element.");
            }

            if ( mNext.wasRemoved() )
            {
                throw new ConcurrentModificationException("Current element was removed.");
            }

            mLast = mNext;
            mNext = Node.getNextOverlapper(mNext,mStart,mEnd);
            return mLast;
        }

        public void remove()
        {
            if ( mLast == null )
            {
                throw new IllegalStateException("No entry to remove.");
            }

            removeNode(mLast);
            mLast = null;
        }

        private Node<V> mNext;
        private Node<V> mLast;
        private int mStart;
        private int mEnd;
    }

    public static class ValuesIterator<V1>
        implements Iterator<V1>
    {
        public ValuesIterator( Iterator<Node<V1>> itr )
        {
            mItr = itr;
        }

        public boolean hasNext()
        {
            return mItr.hasNext();
        }

        public V1 next()
        {
            return mItr.next().getValue();
        }

        public void remove()
        {
            mItr.remove();
        }

        private Iterator<Node<V1>> mItr;
    }
}