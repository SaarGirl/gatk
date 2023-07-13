package org.broadinstitute.hellbender.tools;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMLineParser;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.SAMRecordToGATKReadAdapter;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LocalSVAssemblerUnitTest {

    @Test
    public void testTrimOverruns() {
        final SAMFileHeader header =
                ArtificialReadUtils.createArtificialSamHeader(1, 1, 100000000);
        final SAMLineParser samLineParser = new SAMLineParser(header);

        // two reads that start and end at the same place with a removable soft clip appropriately placed on each
        final GATKRead read1 = new SAMRecordToGATKReadAdapter(
                samLineParser.parseLine("read1\t163\t1\t5113820\t49\t111M40S\t=\t5113820\t111\t"+
                        "ACAGAGACAGGAAGAAGTACGCGTGGGGGGCCCAGTCTGGATATGCTGAGTGGGCGGTGCCCCACTCCAAGTGTAGTGCACAGAGAAGGGCTGGAGTTACAGGCCTCCTTGAGATCGGAAGAGCGTCGTGTAGGGAAAGAGTGTGTATTGC\t"+
                        "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"));
        final GATKRead mate1 = new SAMRecordToGATKReadAdapter(
                samLineParser.parseLine("mate1\t83\t1\t5113820\t49\t40S111M\t=\t5113820\t-111\t"+
                        "GTTGGTGTGACTGGAGTTCAGACGTGTGCTCTTCCGATCTACAGAGACAGGAAGAAGTACGCGTGGGGGGCCCAGTCTGGATATGCTGAGTGGGCGGTGCCCCACTCCAAGTGTAGTGCACAGAGAAGGGCTGGAGTTACAGGCCTCCTTG\t"+
                        "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"));
        LocalSVAssembler.trimOverruns(read1, mate1);

        // expected results trim soft clip from each read
        Assert.assertEquals(read1.getCigar().toString(), "111M40H"); // changed 111M40S to 111M40H
        Assert.assertEquals(read1.getBasesNoCopy().length, 111); // hard-clipped the calls and quals
        Assert.assertEquals(read1.getBaseQualitiesNoCopy().length, 111);
        Assert.assertEquals(mate1.getCigar().toString(), "40H111M"); // changed 40S111M to 40H111M
        Assert.assertEquals(mate1.getBasesNoCopy().length, 111); // hard-clipped the calls and quals
        Assert.assertEquals(mate1.getBaseQualitiesNoCopy().length, 111);
    }


    @Test
    public void testNoTrimOverruns() {
        final SAMFileHeader header =
                ArtificialReadUtils.createArtificialSamHeader(1, 1, 100000000);
        final SAMLineParser samLineParser = new SAMLineParser(header);
        // can't trim this read pair:  cigar is 4S56M91S and the 4S suggests this might be chimeric
        final GATKRead read1 = new SAMRecordToGATKReadAdapter(
                samLineParser.parseLine("read1\t99\t1\t5114132\t0\t4S56M91S\t=\t5114132\t56\t"+
                        "GAGCTGGGGGTTGAGTGTGGAGGAGCTGGGAGTGGTGGGGGAGCTGGGGGTTGAGTGTGGAGGAGCTGGGAGTGGTGGGGGGGCTGGGGGTTGAGTGTGGAGGTGCTGGGAGCGGTGGGGGGGCTGGGGGTTGAGTGTGGAGGTCGGGGGA\t"+
                        "??????????????????????????????????????????????????????????????+5?????????????????+?5???????5???+??????++5&55???5$+??+5?5+555???5??55?+5555??+??########"));
        final GATKRead mate1 = new SAMRecordToGATKReadAdapter(
                samLineParser.parseLine("mate1\t147\t1\t5114132\t0\t95S56M\t=\t5114132\t-56\t"+
                        "GTAGGGTGTGGGGGGTGGGGTGGGGGTGGGGGGGCGGGGGGGGTCGGGGGGGGGGTGGGGGTTGGGTGGGGGGGCGACGGGGTTGGGGGGGGGGCTGGGGGTTGAGGGTGGAGGAGCTGGGAGTGGGGGGGGAGCTGGGGGTTGAGTGTGG\t"+
                        "###############################################################################?55++5'5?'555'5++???55++555'&+?'5??55++??555+?5'????????????????????????"));
        LocalSVAssembler.trimOverruns(read1, mate1);

        // expected results are all unchanged from original
        Assert.assertEquals(read1.getCigar().toString(), "4S56M91S");
        Assert.assertEquals(read1.getBasesNoCopy().length, 151);
        Assert.assertEquals(read1.getBaseQualitiesNoCopy().length, 151);
        Assert.assertEquals(mate1.getCigar().toString(), "95S56M");
        Assert.assertEquals(mate1.getBasesNoCopy().length, 151);
        Assert.assertEquals(mate1.getBaseQualitiesNoCopy().length, 151);
    }
}