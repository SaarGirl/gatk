cat $1 | jq -r '.calls["GvsExtractCallset.ExtractTask"] | .[] | [.shardIndex, .attempt, .preemptible, .executionStatus, .start, .end, .jobId] | @tsv '