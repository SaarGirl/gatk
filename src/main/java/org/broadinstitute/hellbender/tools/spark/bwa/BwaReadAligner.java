package org.broadinstitute.hellbender.tools.spark.bwa;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.utils.bwa.*;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.SAMRecordToGATKReadAdapter;

import java.util.*;

public final class BwaReadAligner {
    private final BwaMemIndex bwaMemIndex;
    private final SAMFileHeader readsHeader;
    private final boolean alignsPairs;
    private final boolean retainDuplicateFlag;
    private int nThreads = 1;

    // assumes 128Mb partitions, with reads needing about 100bytes each when BAM compressed
    private static final int READS_PER_PARTITION_GUESS = 1500000;

    public BwaReadAligner(final String indexFileName, final SAMFileHeader readsHeader, final boolean alignsPairs,
                   final boolean retainDuplicateFlag) {
        this.bwaMemIndex = BwaMemIndexCache.getInstance(indexFileName);
        this.readsHeader = readsHeader;
        this.alignsPairs = alignsPairs;
        this.retainDuplicateFlag = retainDuplicateFlag;
        if (alignsPairs && readsHeader.getSortOrder() != SAMFileHeader.SortOrder.queryname) {
            throw new UserException("Input must be queryname sorted unless you use single-ended alignment mode.");
        }
    }

    public void setNThreads(final int n) {
        nThreads = n;
    }

    public Iterator<GATKRead> apply(final Iterator<GATKRead> readItr) {
        final List<GATKRead> inputReads = new ArrayList<>(READS_PER_PARTITION_GUESS);
        while (readItr.hasNext()) {
            inputReads.add(readItr.next());
        }
        final int nReads = inputReads.size();
        if (alignsPairs) {
            if ((nReads & 1) != 0) {
                throw new GATKException("We're supposed to be aligning paired reads, but there are an odd number of them.");
            }
            for (int idx = 0; idx != nReads; idx += 2) {
                final String readName1 = inputReads.get(idx).getName();
                final String readName2 = inputReads.get(idx + 1).getName();
                if (!Objects.equals(readName1, readName2)) {
                    throw new GATKException("Read pair has varying template name: " + readName1 + " .vs " + readName2);
                }
            }
        }
        final List<List<BwaMemAlignment>> allAlignments;
        if (nReads == 0) allAlignments = Collections.emptyList();
        else {
            final List<byte[]> seqs = new ArrayList<>(nReads);
            for (final GATKRead read : inputReads) {
                seqs.add(read.getBases());
            }
            final BwaMemAligner aligner = new BwaMemAligner(bwaMemIndex);
            aligner.setNThreadsOption(nThreads);
            // we are dealing with interleaved, paired reads.  tell BWA that they're paired.
            if (alignsPairs) {
                aligner.alignPairs();
            }
            allAlignments = aligner.alignSeqs(seqs);
        }
        final List<String> refNames = bwaMemIndex.getReferenceContigNames();
        final List<GATKRead> outputReads = new ArrayList<>(allAlignments.stream().mapToInt(List::size).sum());
        for (int idx = 0; idx != nReads; ++idx) {
            final GATKRead originalRead = inputReads.get(idx);
            final String readName = originalRead.getName();
            final byte[] bases = originalRead.getBases();
            final byte[] quals = originalRead.getBaseQualities();
            final String readGroup = originalRead.getReadGroup();
            final List<BwaMemAlignment> alignments = allAlignments.get(idx);
            final Map<BwaMemAlignment, String> saTagMap = BwaMemAlignmentUtils.createSATags(alignments, refNames);
            for (final BwaMemAlignment alignment : alignments) {
                final boolean isDuplicate = retainDuplicateFlag && originalRead.isDuplicate();
                final SAMRecord samRecord =
                        BwaMemAlignmentUtils.applyAlignment(readName, bases, quals, readGroup,
                                alignment, refNames, readsHeader, false, true, isDuplicate);
                final GATKRead rec = SAMRecordToGATKReadAdapter.headerlessReadAdapter(samRecord);
                final String saTag = saTagMap.get(alignment);
                if (saTag != null) rec.setAttribute("SA", saTag);
                outputReads.add(rec);
            }
        }
        return outputReads.iterator();
    }
}