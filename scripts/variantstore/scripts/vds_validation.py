import argparse
import hail as hl
import itertools

###
# VDS validation:
# check that the reference and variant matrix tables contain the same samples
# check that the reference blocks:
#   * do not have any GQ0s (they should be no calls instead)
#   * are valid in that they have an END after START
#   * are not longer than 1000 bases (this should be true for the ref tables in BQ)
# spot check a small subset of the VDS ## TODO it might be helpful to print rows from this subset
# run the classic Hail method vds.validate()
###




def check_samples_match(vds):
	print('checking sample equivalence between reference and variant MTs')
	assert vds.reference_data.cols().select().collect() == vds.variant_data.cols().select().collect()

def check_ref_blocks(vds):
	print('checking that:\n  * no reference blocks have GQ=0\n  * all ref blocks have END after start\n  * all ref blocks are max 1000 bases long')
	rd = vds.reference_data
	rd = rd.annotate_rows(locus_start = rd.locus.position)

	LEN = rd.END - rd.locus_start + 1

	print('checking that: no reference blocks have GQ=0')
	assert rd.aggregate_entries(hl.agg.all(hl.all(rd.GQ > 0)))

	print('checking that: all ref blocks have END after start')
	assert rd.aggregate_entries(hl.agg.all(hl.all(LEN >= 0)))

	print('checking that: all ref blocks are max 1000 bases long')
	assert rd.aggregate_entries(hl.agg.all(hl.all(LEN <= rd.ref_block_max_length)))

def check_densify_small_region(vds):
	print('running densify on 200kb region')
	from time import time
	t1 = time()

	filt = hl.vds.filter_intervals(vds, [hl.parse_locus_interval('chr16:29.5M-29.7M', reference_genome='GRCh38')])
	n=hl.vds.to_dense_mt(filt).select_entries('LGT')._force_count_rows()

	print(f'took {time() - t1:.1f}s to densify {n} rows after interval query')



def main(vds):
    check_densify_small_region(vds)
    chrs = [f'chr{c}' for c in itertools.chain(range(1, 23), ['X', 'Y', 'M'])]
    for chromosome_to_validate in chrs:
        filtered_vd = vds.variant_data.filter_rows(vds.variant_data.locus.contig == chromosome_to_validate)
        filtered_rd = vds.reference_data.filter_rows(vds.reference_data.locus.contig == chromosome_to_validate)
        filtered_vds = hl.vds.VariantDataset(filtered_rd, filtered_vd)
        print(f"Validating VDS chromosome {chromosome_to_validate}...")
        check_ref_blocks(filtered_vds)
        print(f"Hail VDS validation successful for chromosome {chromosome_to_validate}")

    check_samples_match(vds)
    vds.validate()
    print('Full VDS validation successful')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(allow_abbrev=False, description='Create VAT inputs TSV')
    parser.add_argument('--vds-path', type=str, help='Input VDS Path', required=True)
    parser.add_argument('--temp-path', type=str, help='Path to temporary directory',
                        required=True)

    args = parser.parse_args()

    hl.init(tmp_dir=f'{args.temp_path}/hail_tmp_general')

    vds = hl.vds.read_vds(args.vds_path)

    main(vds)