/**
 * $Id: Scorer.java 72512 2008-09-12 17:16:43Z tsharpe $
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package edu.mit.broad.tedsUtils.align;

import java.util.Arrays;

import edu.mit.broad.tedsUtils.SysProp;

/**
 * Scoring model for alignments.
 *
 * @author tsharpe
 * @version $Revision$
 */
public final class Scorer
{
    /**
     * A default-value scoring model.
     * Matches are 4.  Mismatches are -6.  Matches against N are 0.  Gap opening is -13.  Gap extension is -3.
     * You can override these values with command-line parameters:
     * -DMatchScore=<float>
     * -DMismatchScore=<float>
     * -DNMatchScore=<float>
     * -DGapOpeningScore=<float>
     * -DGapExtensionScore=<float>
     */
    public Scorer()
    {
        this(MATCH_SCORE,MISMATCH_SCORE,NMATCH_SCORE,GAP_OPENING_SCORE,GAP_EXTENSION_SCORE);
    }

    /**
     * A scoring model that allows you to specify match, mismatch, N-match, gap-opening, and gap-extension scores.
     * Ambiguity codes are treated as a match if they have any base in common.  (For example, K matches R, since
     * they both contain G.)
     */
    public Scorer( float matchScore, float mismatchScore, float nMatchScore, float gapOpeningScore, float gapExtensionScore )
    {
        this(createScoringMatrix(matchScore,mismatchScore,nMatchScore),new AffineGapScorer(gapOpeningScore,gapExtensionScore));
    }

    /**
     * A scoring model that allows you to specify the complete match/mismatch matrix, and one gap model used for both X and Y gaps.
     * Match scores should be positive, and mismatch scores should be negative.  This is not checked.
     */
    public Scorer( float[] scoringMatrix, AffineGapScorer gapScorer )
    {
        this(scoringMatrix,gapScorer,gapScorer);
    }

    /**
     * A completely general, totally specifiable scoring model.
     * Match scores should be positive, and mismatch scores should be negative.  This is not checked.
     */
    public Scorer( float[] scoringMatrix, AffineGapScorer xGapScorer, AffineGapScorer yGapScorer )
    {
        if ( scoringMatrix == null )
        {
            throw new IllegalArgumentException("scoring matrix may not be null.");
        }
        if ( scoringMatrix.length != BaseCall.N_BASECALLS*BaseCall.N_BASECALLS )
        {
            throw new IllegalArgumentException("scoring matrix has wrong size.");
        }
        if ( xGapScorer == null )
        {
            throw new IllegalArgumentException("x-gap scorer may not be null.");
        }
        if ( yGapScorer == null )
        {
            throw new IllegalArgumentException("y-gap scorer may not be null.");
        }
        mScoringMatrix = scoringMatrix;
        mXGapScorer = xGapScorer;
        mYGapScorer = yGapScorer;
    }

    /**
     * Gets the appropriate value from the scoring matrix.
     * @param baseEnumX Any ordinal value from the Bases enum.
     * @param baseEnumY Any ordinal value from the Bases enum.
     * @return The score.
     */
    public float getScore( int baseEnumX, int baseEnumY )
    {
        return mScoringMatrix[BaseCall.N_BASECALLS*baseEnumY+baseEnumX];
    }

    /**
     * Gets the AffineGapScorer for gaps in the X sequence.
     */
    public AffineGapScorer getXGapScorer()
    {
        return mXGapScorer;
    }

    /**
     * Gets the AffineGapScorer for gaps in the Y sequence.
     */
    public AffineGapScorer getYGapScorer()
    {
        return mYGapScorer;
    }

    private static float[] createScoringMatrix( float matchScore, float mismatchScore, float nMatchScore )
    {
        if ( matchScore < 0.F || mismatchScore > 0.F )
        {
            throw new IllegalArgumentException("Match scores must be non-negative, mismatch scores must be non-positive.");
        }
        float[] result = new float[BaseCall.N_BASECALLS*BaseCall.N_BASECALLS];
        Arrays.fill(result,mismatchScore);
        int nOrdinal = BaseCall.N.ordinal();
        for ( int yyy = 0; yyy < BaseCall.N_BASECALLS; ++yyy )
        {
            for ( int xxx = 0; xxx < BaseCall.N_BASECALLS; ++xxx )
            {
                if ( (xxx & yyy) != 0 )
                {
                    float val = matchScore;
                    if ( xxx == nOrdinal || yyy == nOrdinal )
                    {
                        val = nMatchScore;
                    }
                    result[BaseCall.N_BASECALLS*yyy+xxx] = val;
                }
            }
        }
        return result;
    }

    private float[] mScoringMatrix;
    private AffineGapScorer mXGapScorer;
    private AffineGapScorer mYGapScorer;

    private static float MATCH_SCORE = SysProp.fVal("MatchScore",4);
    private static float MISMATCH_SCORE = SysProp.fVal("MismatchScore",-6);
    private static float NMATCH_SCORE = SysProp.fVal("NMatchScore",0);
    private static float GAP_OPENING_SCORE = SysProp.fVal("GapOpeningScore",-13);
    private static float GAP_EXTENSION_SCORE = SysProp.fVal("GapExtensionScore",-3);
}