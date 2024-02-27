version 1.0

task GetToolVersions {
  input {
    String? git_branch_or_tag
  }

  meta {
    # Don't even think about caching this.
    volatile: true
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"
  String cloud_sdk_docker_decl = "gcr.io/google.com/cloudsdktool/cloud-sdk:435.0.0-alpine"

  # For GVS releases, set `version` to match the release branch name, e.g. gvs_<major>.<minor>.<patch>.
  # For non-release, leave the value at "unspecified".
  String version = "unspecified"

  String effective_version = select_first([git_branch_or_tag, version])

  String workspace_id_output = "workspace_id.txt"
  String workspace_name_output = "workspace_name.txt"
  String workspace_namespace_output = "workspace_namespace.txt"
  String workspace_bucket_output = "workspace_bucket.txt"
  String submission_id_output = "submission_id.txt"
  String workflow_id_output = "workflow_id.txt"
  String google_project_output = "google_project.txt"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    # Scrape out various workflow / workspace info from the localization and delocalization scripts.
    sed -n -E 's!.*gs://fc-(secure-)?([^\/]+).*!\2!p' /cromwell_root/gcs_delocalization.sh | sort -u > ~{workspace_id_output}
    sed -n -E 's!.*(gs://(fc-(secure-)?[^\/]+)).*!\1!p' /cromwell_root/gcs_delocalization.sh | sort -u > ~{workspace_bucket_output}
    sed -n -E 's!.*gs://fc-(secure-)?([^\/]+)/submissions/([^\/]+).*!\3!p' /cromwell_root/gcs_delocalization.sh | sort -u > ~{submission_id_output}
    sed -n -E 's!.*gs://fc-(secure-)?([^\/]+)/submissions/([^\/]+)/([^\/]+)/([^\/]+).*!\5!p' /cromwell_root/gcs_delocalization.sh | sort -u > ~{workflow_id_output}
    sed -n -E 's!.*(terra-[0-9a-f]+).*# project to use if requester pays$!\1!p' /cromwell_root/gcs_localization.sh | sort -u > ~{google_project_output}

    echo "~{effective_version}" > version.txt

    # Only get the git hash if a branch or tag was specified.
    if [[ "~{effective_version}" == "unspecified" ]]
    then
      echo "unspecified" > git_hash.txt
    else
      bash ~{monitoring_script} > monitoring.log &

      # install git
      apk update && apk upgrade
      apk add git

      # The `--branch` parameter to `git clone` actually does work for tags, though for historical reasons GVS
      # versioning is based on branches for now.
      # https://git-scm.com/docs/git-clone#Documentation/git-clone.txt--bltnamegt
      git clone https://github.com/broadinstitute/gatk.git --depth 1 --branch ~{effective_version} --single-branch
      cd gatk
      git rev-parse HEAD > ../git_hash.txt
    fi
  >>>
  runtime {
    docker: cloud_sdk_docker_decl
  }
  output {
    String gvs_version = read_string("version.txt")
    String git_hash = read_string("git_hash.txt")
    String hail_version = "0.2.126"
    String basic_docker = "ubuntu:22.04"
    String cloud_sdk_docker = cloud_sdk_docker_decl # Defined above as a declaration.
    # GVS generally uses the smallest `alpine` version of the Google Cloud SDK as it suffices for most tasks, but
    # there are a handlful of tasks that require the larger GNU libc-based `slim`.
    String cloud_sdk_slim_docker = "gcr.io/google.com/cloudsdktool/cloud-sdk:435.0.0-slim"
    String variants_docker = "us.gcr.io/broad-dsde-methods/variantstore:2024-01-30-alpine-f228c2f81"
    String gatk_docker = "us.gcr.io/broad-dsde-methods/broad-gatk-snapshots:varstore_2024_01_17_c1ac3790f65266568937bf1fd29bbd71b2879a4f"
    String variants_nirvana_docker = "us.gcr.io/broad-dsde-methods/variantstore:nirvana_2022_10_19"
    String real_time_genomics_docker = "docker.io/realtimegenomics/rtg-tools:latest"
    String gotc_imputation_docker = "us.gcr.io/broad-gotc-prod/imputation-bcf-vcf:1.0.5-1.10.2-0.1.16-1649948623"

    String workspace_bucket = read_string(workspace_bucket_output)
    String workspace_id = read_string(workspace_id_output)
    String submission_id = read_string(submission_id_output)
    String workflow_id = read_string(workflow_id_output)
    String google_project = read_string(google_project_output)
  }
}

task MergeVCFs {
  input {
    Array[File] input_vcfs
    String gather_type = "BLOCK"
    String output_vcf_name
    String? output_directory
    Int? merge_disk_override
    Int? preemptible_tries
    String gatk_docker
  }

  Int disk_size = select_first([merge_disk_override, 100])
  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  parameter_meta {
    input_vcfs: {
      localization_optional: true
    }
  }

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    gatk --java-options -Xmx3g GatherVcfsCloud \
      --ignore-safety-checks \
      --gather-type ~{gather_type} \
      --create-output-variant-index false \
      -I ~{sep=' -I ' input_vcfs} \
      --progress-logger-frequency 100000 \
      --output ~{output_vcf_name}

    tabix ~{output_vcf_name}

    # Drop trailing slash if one exists
    OUTPUT_GCS_DIR=$(echo ~{output_directory} | sed 's/\/$//')

    if [ -n "$OUTPUT_GCS_DIR" ]; then
      gsutil cp ~{output_vcf_name} $OUTPUT_GCS_DIR/
      gsutil cp ~{output_vcf_name}.tbi $OUTPUT_GCS_DIR/
    fi
  >>>

  runtime {
    docker: gatk_docker
    preemptible: select_first([preemptible_tries, 3])
    memory: "3 GiB"
    disks: "local-disk ~{disk_size} HDD"
  }

  output {
    File output_vcf = "~{output_vcf_name}"
    File output_vcf_index = "~{output_vcf_name}.tbi"
    File monitoring_log = "monitoring.log"
  }
}

task SplitIntervals {
  input {
    File intervals
    File ref_fasta
    File ref_fai
    File ref_dict
    Int scatter_count
    File? interval_weights_bed
    String? intervals_file_extension
    String? split_intervals_extra_args
    Int? split_intervals_disk_size_override
    Int? split_intervals_mem_override
    String? output_gcs_dir
    String gatk_docker
    File? gatk_override
  }
  meta {
    # Not `volatile: true` since there shouldn't be a need to re-run this if there has already been a successful execution.
  }

  Int disk_size = select_first([split_intervals_disk_size_override, 50]) # Note: disk size is cheap and lack of it can increase probability of preemption
  Int disk_memory = select_first([split_intervals_mem_override, 16])
  Int java_memory = disk_memory - 4

  String gatk_tool = if (defined(interval_weights_bed)) then 'WeightedSplitIntervals' else 'SplitIntervals'
  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  parameter_meta {
    intervals: {
      localization_optional: true
    }
    ref_fasta: {
      localization_optional: true
    }
    ref_fai: {
      localization_optional: true
    }
    ref_dict: {
      localization_optional: true
    }
  }

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk_override}

    mkdir interval-files
    gatk --java-options "-Xmx~{java_memory}g" ~{gatk_tool} \
      --dont-mix-contigs \
      -R ~{ref_fasta} \
      ~{"-L " + intervals} \
      ~{"--weight-bed-file " + interval_weights_bed} \
      -scatter ~{scatter_count} \
      -O interval-files \
      ~{"--extension " + intervals_file_extension} \
      --interval-file-num-digits 10 \
      ~{split_intervals_extra_args}
    cp interval-files/*.interval_list .

    # Drop trailing slash if one exists
    OUTPUT_GCS_DIR=$(echo ~{output_gcs_dir} | sed 's/\/$//')

    if [ -n "$OUTPUT_GCS_DIR" ]; then
      gsutil -m cp *.interval_list $OUTPUT_GCS_DIR/
    fi
  >>>

  runtime {
    docker: gatk_docker
    bootDiskSizeGb: 15
    memory: "~{disk_memory} GB"
    disks: "local-disk ~{disk_size} HDD"
    preemptible: 3
    cpu: 1
  }

  output {
    Array[File] interval_files = glob("*.interval_list")
    File monitoring_log = "monitoring.log"
  }
}

task GetBQTableLastModifiedDatetime {
  input {
    Boolean go = true
    String project_id
    String fq_table
    String cloud_sdk_docker
  }
  meta {
    # because this is being used to determine if the data has changed, never use call cache
    volatile: true
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  # ------------------------------------------------
  # try to get the last modified date for the table in question; fail if something comes back from BigQuery
  # that isn't in the right format (e.g. an error)
  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    echo "project_id = ~{project_id}" > ~/.bigqueryrc

    # bq needs the project name to be separate by a colon
    DATASET_TABLE_COLON=$(echo ~{fq_table} | sed 's/\./:/')

    LASTMODIFIED=$(bq --apilog=false --project_id=~{project_id} --format=json show ${DATASET_TABLE_COLON} | python3 -c "import sys, json; print(json.load(sys.stdin)['lastModifiedTime']);")
    if [[ $LASTMODIFIED =~ ^[0-9]+$ ]]; then
      echo $LASTMODIFIED
    else
      exit 1
    fi
  >>>

  output {
    String last_modified_timestamp = read_string(stdout())
    File monitoring_log = "monitoring.log"
  }

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 10 HDD"
    preemptible: 3
    cpu: 1
  }
}

task GetBQTablesMaxLastModifiedTimestamp {
  input {
    String query_project
    String data_project
    String dataset_name
    Array[String] table_patterns
    String cloud_sdk_docker
  }
  meta {
    # because this is being used to determine if the data has changed, never use call cache
    volatile: true
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  # ------------------------------------------------
  # try to get the latest last modified timestamp, in epoch microseconds, for all of the tables that match the provided prefixes
  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    echo "project_id = ~{query_project}" > ~/.bigqueryrc

    bq --apilog=false --project_id=~{query_project} query --format=csv --use_legacy_sql=false \
    'SELECT UNIX_MICROS(MAX(last_modified_time)) last_modified_time FROM `~{data_project}`.~{dataset_name}.INFORMATION_SCHEMA.PARTITIONS WHERE table_name like "~{sep=" OR table_name like " table_patterns}"' > results.txt

    tail -1 results.txt | cut -d, -f1 > max_last_modified_timestamp.txt
  >>>

  output {
    String max_last_modified_timestamp = read_string("max_last_modified_timestamp.txt")
    File monitoring_log = "monitoring.log"
  }

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 10 HDD"
    preemptible: 3
    cpu: 1
  }
}

task BuildGATKJar {
  input {
    String? git_branch_or_tag
    String cloud_sdk_slim_docker
  }
  meta {
    # Branch may be updated so do not call cache!
    volatile: true
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    # git and git-lfs
    apt-get -qq update
    apt-get -qq install git git-lfs

    # Temurin Java 17
    # https://adoptium.net/installation/linux/
    apt install -y wget apt-transport-https
    mkdir -p /etc/apt/keyrings
    wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /etc/apt/keyrings/adoptium.asc
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
    apt-get -qq update
    apt-get -qq install temurin-17-jdk

    # GATK
    git clone https://github.com/broadinstitute/gatk.git --depth 1 --branch ~{git_branch_or_tag} --single-branch
    cd gatk
    ./gradlew shadowJar

    branch=$(git symbolic-ref HEAD 2>/dev/null)
    branch=${branch#refs/heads/}

    short_hash=$(git rev-parse --short HEAD)

    # Rename the GATK jar to embed the branch and hash of the most recent commit on the branch.
    mv build/libs/gatk-package-unspecified-SNAPSHOT-local.jar "build/libs/gatk-${branch}-${short_hash}-SNAPSHOT-local.jar"

    git rev-parse HEAD > ../git_hash.txt
  >>>

  output {
    Boolean done = true
    File jar = glob("gatk/build/libs/*-SNAPSHOT-local.jar")[0]
    File monitoring_log = "monitoring.log"
    String git_hash = read_string("git_hash.txt")
  }

  runtime {
    docker: cloud_sdk_slim_docker
    disks: "local-disk 500 HDD"
  }
}

task CreateDatasetForTest {
  input {
    String? git_branch_or_tag
    String dataset_prefix
    String dataset_suffix
    String cloud_sdk_docker
    # By default auto-expire tables 2 weeks after their creation. Unfortunately there doesn't seem to be an automated way
    # of auto-expiring the dataset, but the date is in the dataset name so old datasets should be easy to identify.
    Int? table_ttl_seconds = 2 * 7 * 24 * 60 * 60
  }
  meta {
    description: "Create a dataset for testing purposes whose tables are all set to auto-expire. Do not call this task for production code as the tables created within it will auto-delete!"
    # Branch may be updated so do not call cache!
    volatile: true
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    # git
    apk update && apk upgrade
    apk add git

    # GATK
    git clone https://github.com/broadinstitute/gatk.git --depth 1 --branch ~{git_branch_or_tag} --single-branch
    cd gatk

    branch=$(git symbolic-ref HEAD 2>/dev/null)
    branch=${branch#refs/heads/}

    hash=$(git rev-parse --short HEAD)

    # Build a dataset name based on the branch name and the git hash of the most recent commit on this branch.
    # Dataset names must be alphanumeric and underscores only. Convert any dashes to underscores, then delete
    # any remaining characters that are not alphanumeric or underscores.
    today="$(date -Idate | sed 's/-/_/g')"
    dataset="$(echo ~{dataset_prefix}_${today}_${branch}_${hash}_~{dataset_suffix} | tr '-' '_' | tr -c -d '[:alnum:]_')"

    bq --apilog=false mk --project_id="gvs-internal" --default_table_expiration="~{table_ttl_seconds}" "$dataset"

    # add labels for DSP Cloud Cost Control Labeling and Reporting
    bq --apilog=false update --set_label service:gvs gvs-internal:$dataset
    bq --apilog=false update --set_label team:variants gvs-internal:$dataset
    bq --apilog=false update --set_label environment:dev gvs-internal:$dataset
    bq --apilog=false update --set_label managedby:build_gatk_jar_and_create_dataset gvs-internal:$dataset

    echo -n "$dataset" > dataset.txt
  >>>

  output {
    Boolean done = true
    String dataset_name = read_string("gatk/dataset.txt")
    File monitoring_log = "monitoring.log"
  }

  runtime {
    docker: cloud_sdk_docker
    disks: "local-disk 500 HDD"
  }
}

task BuildGATKJarAndCreateDataset {
  input {
    String? git_branch_or_tag
    String dataset_prefix
    String dataset_suffix
    String cloud_sdk_slim_docker
  }
  meta {
    # Branch may be updated so do not call cache!
    volatile: true
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    # git and git-lfs
    apt-get -qq update
    apt-get -qq install git git-lfs

    # Temurin Java 17
    # https://adoptium.net/installation/linux/
    apt install -y wget apt-transport-https
    mkdir -p /etc/apt/keyrings
    wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /etc/apt/keyrings/adoptium.asc
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
    apt-get -qq update
    apt-get -qq install temurin-17-jdk

    # GATK
    git clone https://github.com/broadinstitute/gatk.git --depth 1 --branch ~{git_branch_or_tag} --single-branch
    cd gatk
    ./gradlew shadowJar

    branch=$(git symbolic-ref HEAD 2>/dev/null)
    branch=${branch#refs/heads/}

    hash=$(git rev-parse --short HEAD)

    # Rename the GATK jar to embed the branch and hash of the most recent commit on the branch.
    mv build/libs/gatk-package-unspecified-SNAPSHOT-local.jar "build/libs/gatk-${branch}-${hash}-SNAPSHOT-local.jar"

    # Build a dataset name based on the branch name and the git hash of the most recent commit on this branch.
    # Dataset names must be alphanumeric and underscores only. Convert any dashes to underscores, then delete
    # any remaining characters that are not alphanumeric or underscores.
    dataset="$(echo ~{dataset_prefix}_${branch}_${hash}_~{dataset_suffix} | tr '-' '_' | tr -c -d '[:alnum:]_')"

    bq --apilog=false mk --project_id="gvs-internal" "$dataset"

    # add labels for DSP Cloud Cost Control Labeling and Reporting
    bq --apilog=false update --set_label service:gvs gvs-internal:$dataset
    bq --apilog=false update --set_label team:variants gvs-internal:$dataset
    bq --apilog=false update --set_label environment:dev gvs-internal:$dataset
    bq --apilog=false update --set_label managedby:build_gatk_jar_and_create_dataset gvs-internal:$dataset

    echo -n "$dataset" > dataset.txt
  >>>

  output {
    Boolean done = true
    File jar = glob("gatk/build/libs/*-SNAPSHOT-local.jar")[0]
    String dataset_name = read_string("gatk/dataset.txt")
    File monitoring_log = "monitoring.log"
  }

  runtime {
    docker: cloud_sdk_slim_docker
    disks: "local-disk 500 HDD"
  }
}

task TerminateWorkflow {
  input {
    String message
    String basic_docker
  }
  meta {
    # Definitely do not call cache this!
    volatile: true
  }

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    # To avoid issues with special characters within the message, write the message to a file.
    cat > message.txt <<FIN
    ~{message}
    FIN

    # cat the file to stderr as this task is going to fail due to the exit 1.
    cat message.txt >&2
    exit 1
  >>>

  runtime {
    docker: basic_docker
    memory: "1 GB"
    disks: "local-disk 10 HDD"
    preemptible: 3
    cpu: 1
  }

  output {
    Boolean done = true
  }
}

task ScaleXYBedValues {
    input {
        Boolean go = true
        File interval_weights_bed
        Float x_bed_weight_scaling
        Float y_bed_weight_scaling
        String variants_docker
    }
    meta {
        # Not `volatile: true` since there shouldn't be a need to re-run this if there has already been a successful execution.
    }
    File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

    command <<<
        # Prepend date, time and pwd to xtrace log entries.
        PS4='\D{+%F %T} \w $ '
        set -o errexit -o nounset -o pipefail -o xtrace

        bash ~{monitoring_script} > monitoring.log &

        python3 /app/scale_xy_bed_values.py \
            --input ~{interval_weights_bed} \
            --output "interval_weights_xy_scaled.bed" \
            --xscale ~{x_bed_weight_scaling} \
            --yscale ~{y_bed_weight_scaling} \
    >>>

    output {
        File xy_scaled_bed = "interval_weights_xy_scaled.bed"
        Boolean done = true
        File monitoring_log = "monitoring.log"
    }

    runtime {
        docker: variants_docker
        maxRetries: 3
        memory: "7 GB"
        preemptible: 3
        cpu: "2"
        disks: "local-disk 500 HDD"
    }
}

task GetNumSamplesLoaded {
  input {
    String fq_sample_table
    String project_id
    String sample_table_timestamp
    Boolean control_samples = false
    String cloud_sdk_docker
  }
  meta {
    # Not `volatile: true` since there shouldn't be a need to re-run this if there has already been a successful execution.
  }
  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    echo "project_id = ~{project_id}" > ~/.bigqueryrc
    bq --apilog=false query --project_id=~{project_id} --format=csv --use_legacy_sql=false '

      SELECT COUNT(*) FROM `~{fq_sample_table}` WHERE
        is_loaded = true AND
        withdrawn IS NULL AND
        is_control = ~{control_samples}

    ' | sed 1d
  >>>

  output {
    Int num_samples = read_int(stdout())
    File monitoring_log = "monitoring.log"
  }

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 10 HDD"
    preemptible: 3
    cpu: 1
  }
}


task CountSuperpartitions {
    meta {
        description: "Return the number of superpartitions based on the number of vet_% tables in `INFORMATION_SCHEMA.PARTITIONS`."
        # Definitely don't cache this, the values can change while the inputs to this task will not!
        volatile: true
    }
    input {
        String project_id
        String dataset_name
        String cloud_sdk_docker
    }
    File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"
    command <<<
        # Prepend date, time and pwd to xtrace log entries.
        PS4='\D{+%F %T} \w $ '
        set -o errexit -o nounset -o pipefail -o xtrace

        bash ~{monitoring_script} > monitoring.log &

        bq --apilog=false query --project_id=~{project_id} --format=csv --use_legacy_sql=false '

            SELECT COUNT(*) FROM `~{project_id}.~{dataset_name}.INFORMATION_SCHEMA.TABLES`
                WHERE table_name LIKE "vet_%"

        ' | sed 1d > num_superpartitions.txt
    >>>
    runtime {
        docker: cloud_sdk_docker
        disks: "local-disk 500 HDD"
    }
    output {
        Int num_superpartitions = read_int('num_superpartitions.txt')
        File monitoring_log = "monitoring.log"
    }
}

task ValidateFilterSetName {
    input {
        Boolean go = true
        String project_id
        String fq_filter_set_info_table
        String filter_set_name
        String filter_set_info_timestamp = ""
        String cloud_sdk_docker
    }
    meta {
        # Not `volatile: true` since there shouldn't be a need to re-run this if there has already been a successful execution.
    }

    # add labels for DSP Cloud Cost Control Labeling and Reporting
    String bq_labels = "--label service:gvs --label team:variants --label managedby:gvs_utils"
    File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

    command <<<
        # Prepend date, time and pwd to xtrace log entries.
        PS4='\D{+%F %T} \w $ '
        set -o errexit -o nounset -o pipefail -o xtrace

        bash ~{monitoring_script} > monitoring.log &

        echo "project_id = ~{project_id}" > ~/.bigqueryrc

        OUTPUT=$(bq --apilog=false --project_id=~{project_id} --format=csv query --use_legacy_sql=false ~{bq_labels} 'SELECT filter_set_name as available_filter_set_names FROM `~{fq_filter_set_info_table}` GROUP BY filter_set_name')
        FILTERSETS=${OUTPUT#"available_filter_set_names"}

        if [[ $FILTERSETS =~ "~{filter_set_name}" ]]; then
            echo "Filter set name '~{filter_set_name}' found."
        else
            echo "ERROR: '~{filter_set_name}' is not an existing filter_set_name. Available in ~{fq_filter_set_info_table} are"
            echo $FILTERSETS
            exit 1
        fi
    >>>
    output {
        Boolean done = true
        File monitoring_log = "monitoring.log"
    }

    runtime {
        docker: cloud_sdk_docker
        memory: "3 GB"
        disks: "local-disk 500 HDD"
        preemptible: 3
        cpu: 1
    }
}

task IsVQSRLite {
  input {
    String project_id
    String fq_filter_set_info_table
    String filter_set_name
    String cloud_sdk_docker
  }
  meta {
    # Not `volatile: true` since there shouldn't be a need to re-run this if there has already been a successful execution.
  }

  # add labels for DSP Cloud Cost Control Labeling and Reporting
  String bq_labels = "--label service:gvs --label team:variants --label managedby:gvs_utils"

  String is_vqsr_lite_file = "is_vqsr_lite_file.txt"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    echo "project_id = ~{project_id}" > ~/.bigqueryrc

    bq --apilog=false query --project_id=~{project_id} --format=csv --use_legacy_sql=false ~{bq_labels} \
    'BEGIN
      SELECT COUNT(1) AS counted FROM `~{fq_filter_set_info_table}` WHERE filter_set_name = "~{filter_set_name}"
          AND calibration_sensitivity IS NOT NULL;
    EXCEPTION WHEN ERROR THEN
       SELECT "0" AS counted ;
    END' | tail -1 > lite_count_file.txt
    LITE_COUNT=`cat lite_count_file.txt`


    bq --apilog=false query --project_id=~{project_id} --format=csv --use_legacy_sql=false ~{bq_labels} \
      'SELECT COUNT(1) FROM `~{fq_filter_set_info_table}` WHERE filter_set_name = "~{filter_set_name}"
      AND vqslod IS NOT NULL' | tail -1 > classic_count_file.txt
    CLASSIC_COUNT=`cat classic_count_file.txt`

    if [[ $LITE_COUNT != "0" ]]; then
      echo "Found $LITE_COUNT rows with calibration_sensitivity defined"
      if [[ $CLASSIC_COUNT != "0" ]]; then
        echo "Found $CLASSIC_COUNT rows with vqslod defined"
        echo "ERROR - can't have both defined for a filter_set"
        exit 1
      fi
      echo "true" > ~{is_vqsr_lite_file}
    elif [[ $CLASSIC_COUNT != "0" ]]; then
      echo "Found $CLASSIC_COUNT rows with vqslod defined"
      echo "false" > ~{is_vqsr_lite_file}
    else
      echo "Found NO rows with either calibration_sensitivity or vqslod defined"
      exit 1
    fi

  >>>
  output {
    Boolean is_vqsr_lite = read_boolean(is_vqsr_lite_file)
  }

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 500 HDD"
    preemptible: 3
    cpu: 1
  }
}

task IsUsingCompressedReferences {
  input {
    String project_id
    String dataset_name
    String ref_table_timestamp
    String cloud_sdk_docker
  }
  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bq --apilog=false query --project_id=~{project_id} --format=csv --use_legacy_sql=false '
      SELECT
        column_name
      FROM
        `~{dataset_name}.INFORMATION_SCHEMA.COLUMNS`
      WHERE
        table_name = "ref_ranges_001"
      AND (column_name = "location" OR column_name = "packed_ref_data") ' | sed 1d > column_name.txt

    # grep will return non-zero if the "query" term is not found and we don't want to fail the task for that.
    set +o errexit

    grep packed_ref_data column_name.txt
    rc=$?
    if [[ $rc -eq 0 ]]
    then
      ret=true
    else
      grep location column_name.txt
      rc=$?
      if [[ $rc -eq 0 ]]
      then
        ret=false
      else
        echo "Did not find either expected column name 'location' or 'packed_ref_data' in ref_ranges_001 table." 1>&2
        exit 1
      fi
    fi
    set -o errexit

    echo $ret > ret.txt
  >>>

  output {
    Boolean is_using_compressed_references = read_boolean("ret.txt")
    File column_name = "column_name.txt"
  }

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 500 HDD"
    preemptible: 3
    cpu: 1
  }
}

task IndexVcf {
    input {
        File input_vcf

        Int memory_mb = 7500
        Int disk_size_gb = ceil(2 * size(input_vcf, "GiB")) + 200
        String gatk_docker
    }

    File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

    Int command_mem = memory_mb - 1000
    Int max_heap = memory_mb - 500

    String local_file = basename(input_vcf)
    Boolean is_compressed = sub(local_file, ".*\\.", "") == "gz"
    String index_extension = if is_compressed then ".tbi" else ".idx"

    command <<<
        # Prepend date, time and pwd to xtrace log entries.
        PS4='\D{+%F %T} \w $ '
        set -o errexit -o nounset -o pipefail -o xtrace

        bash ~{monitoring_script} > monitoring.log &

        # Localize the passed input_vcf to the working directory so the
        # to-be-created index file is also created there, alongside it.
        ln -s ~{input_vcf} ~{local_file}

        gatk --java-options "-Xms~{command_mem}m -Xmx~{max_heap}m" \
            IndexFeatureFile \
            -I ~{local_file}

    >>>

    runtime {
        docker: gatk_docker
        cpu: 1
        memory: "${memory_mb} MiB"
        disks: "local-disk ${disk_size_gb} HDD"
        bootDiskSizeGb: 15
        preemptible: 3
    }

    output {
        File output_vcf_index = "~{local_file}~{index_extension}"
        File monitoring_log = "monitoring.log"
    }
}

task SelectVariants {
    input {
        File input_vcf
        File input_vcf_index
        File? interval_list
        String? type_to_include
        Boolean exclude_filtered = false
        String output_basename

        Int memory_mb = 7500
        Int disk_size_gb = ceil(2*size(input_vcf, "GiB")) + 200
        String gatk_docker
    }

    File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

    Int command_mem = memory_mb - 1000
    Int max_heap = memory_mb - 500

    String local_vcf = basename(input_vcf)
    String local_index = basename(input_vcf_index)

    command <<<
      # Prepend date, time and pwd to xtrace log entries.
      PS4='\D{+%F %T} \w $ '
      set -o errexit -o nounset -o pipefail -o xtrace

      bash ~{monitoring_script} > monitoring.log &

      # Localize the passed input_vcf and input_vcf_index to the working directory so the
      # index and the VCF are side by side in the same directory.
      ln -s ~{input_vcf} ~{local_vcf}
      ln -s ~{input_vcf_index} ~{local_index}

      gatk --java-options "-Xms~{command_mem}m -Xmx~{max_heap}m" \
        SelectVariants \
          -V ~{local_vcf} \
          ~{"-L " + interval_list} \
          ~{"--select-type-to-include " + type_to_include} \
          ~{true="--exclude-filtered true" false="" exclude_filtered} \
          -O ~{output_basename}.vcf
    >>>

    runtime {
        docker: gatk_docker
        cpu: 1
        memory: "${memory_mb} MiB"
        disks: "local-disk ${disk_size_gb} HDD"
        bootDiskSizeGb: 15
        preemptible: 3
    }

    output {
        File output_vcf = "~{output_basename}.vcf"
        File output_vcf_index = "~{output_basename}.vcf.idx"
        File monitoring_log = "monitoring.log"
    }
}

task MergeTsvs {
    input {
        Array[File] input_files
        String output_file_name
        String basic_docker
    }

    File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

    command <<<
      # Prepend date, time and pwd to xtrace log entries.
      PS4='\D{+%F %T} \w $ '
      set -o errexit -o nounset -o pipefail -o xtrace

      bash ~{monitoring_script} > monitoring.log &

      echo -n > ~{output_file_name}
      for f in ~{sep=' ' input_files}
      do
        cat $f >> ~{output_file_name}
      done
    >>>

    runtime {
      docker: basic_docker
    }

    output {
      File output_file = output_file_name
      File monitoring_log = "monitoring.log"
    }

}

task SummarizeTaskMonitorLogs {
  input {
    Array[File] inputs
    String variants_docker
  }

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    INPUTS="~{sep=" " inputs}"
    if [[ -z "$INPUTS" ]]; then
      echo "No monitoring log files found" > monitoring_summary.txt
    else
      python3 /app/summarize_task_monitor_logs.py \
        --input $INPUTS \
        --output monitoring_summary.txt
    fi

  >>>

  # ------------------------------------------------
  # Runtime settings:
  runtime {
    docker: variants_docker
    memory: "1 GB"
    preemptible: 3
    cpu: "1"
    disks: "local-disk 100 HDD"
  }
  output {
    File monitoring_summary = "monitoring_summary.txt"
  }
}

# Note - this task should probably live in GvsCreateFilterSet, but I moved it here when I was refactoring VQSR Classic out of
# GvsCreateFilterSet (in order to avoid a circular dependency)
# When VQSR Classic is removed, consider putting this task back in GvsCreateFilterSet
task PopulateFilterSetInfo {
  input {
    String filter_set_name
    String filter_schema
    String fq_filter_set_info_destination_table
    Boolean useClassic = false

    File snp_recal_file
    File snp_recal_file_index
    File indel_recal_file
    File indel_recal_file_index

    String project_id

    Int memory_mb = 7500
    Int disk_size_gb = ceil(3 * (size(snp_recal_file, "GiB") +
                                 size(snp_recal_file_index, "GiB") +
                                 size(indel_recal_file, "GiB") +
                                 size(indel_recal_file_index, "GiB"))) + 500
    String gatk_docker
    File? gatk_override
  }
  meta {
    # Not `volatile: true` since there shouldn't be a need to re-run this if there has already been a successful execution.
  }

  File monitoring_script = "gs://gvs_quickstart_storage/cromwell_monitoring_script.sh"

  Int command_mem = memory_mb - 1000
  Int max_heap = memory_mb - 500

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    bash ~{monitoring_script} > monitoring.log &

    export GATK_LOCAL_JAR=~{default="/root/gatk.jar" gatk_override}

    echo "Creating SNPs recalibration file"
    gatk --java-options "-Xms~{command_mem}m -Xmx~{max_heap}m" \
      CreateFilteringFiles \
        --ref-version 38 \
        --filter-set-name ~{filter_set_name} \
        -mode SNP \
        --classic ~{useClassic} \
        -V ~{snp_recal_file} \
        -O ~{filter_set_name}.snps.recal.tsv

    echo "Creating INDELs racalibration file"
    gatk --java-options "-Xms~{command_mem}m -Xmx~{max_heap}m" \
      CreateFilteringFiles \
        --ref-version 38 \
        --filter-set-name ~{filter_set_name} \
        -mode INDEL \
        --classic ~{useClassic} \
        -V ~{indel_recal_file} \
        -O ~{filter_set_name}.indels.recal.tsv

    # merge into a single file
    echo "Merging SNP + INDELs"
    cat ~{filter_set_name}.snps.recal.tsv ~{filter_set_name}.indels.recal.tsv | grep -v filter_set_name | grep -v "#"  > ~{filter_set_name}.filter_set_load.tsv

    # BQ load likes a : instead of a . after the project
    bq_table=$(echo ~{fq_filter_set_info_destination_table} | sed s/\\./:/)

    echo "Loading combined TSV into ~{fq_filter_set_info_destination_table}"
    bq --apilog=false load --project_id=~{project_id} --skip_leading_rows 0 -F "tab" \
      --range_partitioning=location,0,26000000000000,6500000000 \
      --clustering_fields=location \
      --schema "~{filter_schema}" \
      ${bq_table} \
      ~{filter_set_name}.filter_set_load.tsv
  >>>

  runtime {
    docker: gatk_docker
    memory: "${memory_mb} MiB"
    disks: "local-disk ~{disk_size_gb} HDD"
    bootDiskSizeGb: 15
    preemptible: 0
    cpu: 1
  }

  output {
    File monitoring_log = "monitoring.log"
  }
}
  

task SnapshotTables {
  input {
    String project_id
    String dataset_name
    String snapshot_dataset
    String run_name
    String retrieval_key
    String? exclude_regex
    Boolean go = true
    String cloud_sdk_docker
  }
  meta {
    # because this is being used to determine if the data has changed, never use call cache
    volatile: true
  }

  String table_mappings_table = "table_mappings"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    # This little snippet
    # 1. Uses bq to get the list of all tables in a dataset
    # 2. drops the first two lines (it's the column names and a line of undescores that are a visual separator)
    # 3. smashes together the whitespace, as the columns are separated by variable amounts and it can confuse cut
    # 4. uses cut to get just the table names

    echo "Running the empty task to snapshot from ~{dataset_name} to ~{snapshot_dataset} associated with key ~{retrieval_key}"

    bq ls --project_id=~{project_id} --max_results 1000 ~{project_id}:~{dataset_name} | \
    tail -n +3 | \
    tr -s ' ' | \
    cut -d ' ' -f 2 > table_names.txt

    # If there is an EXCLUDE regex, run that over the file here too using grep.  We'll want to override the original file
    ~{"grep -vE '" + exclude_regex + "' table_names.txt > filtered_tables.txt && cp filtered_tables.txt table_names.txt"}


    # token based on current date and time
    DT=$(date '+%Y%m%d_%s')

    for tb in `cat table_names.txt`
    do
    echo "Snapshotting $tb"

    # This is where we decide whether or not this table is included.
    # First, look up the timestamp for this table
    LASTMODIFIED=$(bq --apilog=false --project_id=~{project_id} --format=json show ~{dataset_name}.${tb} | python3 -c "import sys, json; print(json.load(sys.stdin)['lastModifiedTime']);")
    echo "Table $tb last modified at timestamp $LASTMODIFIED"

    # Basic validation of the value that we got back
    if [[ $LASTMODIFIED =~ ^[0-9]+$ ]]; then
      echo "Last modified stamp for table ${tb}: $LASTMODIFIED"
    else
      echo "ERROR: Couldn't get proper Last modified stamp for table ${tb}. Got ${LASTMODIFIED}"
    fi


    # This is where we'd look up the metadata table and see if it had an entry for this run already.
    # DOCUMENT THIS SQL LATER
    TABLE_JSON=$(bq --apilog=false --project_id=~{project_id} query --use_legacy_sql=false --format=json \
    "SELECT original_table_last_modified as last_modified, local_table_name FROM ~{project_id}.~{snapshot_dataset}.table_mappings WHERE table_group = \"~{run_name}\" AND original_table_name = \"${tb}\" GROUP BY original_table_last_modified, local_table_name ORDER BY original_table_last_modified DESC LIMIT 1")


    # This returns an empty array if there are no values.  But if there IS a value AND it matches, we can reuse that table
    if [ $TABLE_JSON != "[]" ]; then
      echo "There was a previous entry for this table ${tb}.  Seeing if we can use it"

      # Pull out the timestamp from the bq json and validate it
      LASTSTOREDMODIFIED=`echo $TABLE_JSON | python3 -c "import sys, json; print(json.load(sys.stdin)[0]['last_modified']);"`
      if [[ $LASTSTOREDMODIFIED =~ ^[0-9]+$ ]]; then
        echo "Last stored copy during this run was modified at time: ${LASTSTOREDMODIFIED}"
      else
        echo "unable to parse the last stored value for this table"
        exit 1
      fi

      if [ $LASTSTOREDMODIFIED == $LASTMODIFIED ]; then
        # pull out the table name we'll use from the json as well
        PREEXISTING_TABLE=`echo $TABLE_JSON | python3 -c "import sys, json; print(json.load(sys.stdin)[0]['local_table_name']);"`
        echo "Since the data hasn't changed, using existing table $PREEXISTING_TABLE"

        # make a record of the table and the associated key for looking it back up
        bq --apilog=false --project_id=~{project_id} query --use_legacy_sql=false \
        "INSERT INTO ~{snapshot_dataset}.table_mappings (table_group, key, local_table_name, original_table_name, original_table_last_modified, is_not_modified) VALUES (\"~{run_name}\", \"~{retrieval_key}\", \"${PREEXISTING_TABLE}\", \"${tb}\", $LASTMODIFIED, true)"
        continue
      fi
    fi

    # We couldn't find an old table to reuse.  Copy it over ane make a new entry

    echo "There was no previous version that we could use"
    # This is the first time we've seen this particular table in this grouping (usually a run of a single callset) so we need to copy the table over

    # we want to avoid collisions when the same table is copied more than once, so throw that time token on the end
    DEST_TABLE="${tb}_${DT}"

    echo "Moving ~{project_id}:~{dataset_name}.${tb} to ~{project_id}:~{snapshot_dataset}.${DEST_TABLE}"

    # actually copy the tables over into the holding dataset
    bq cp \
    --project_id="~{project_id}" \
    --no_clobber \
    ~{project_id}:~{dataset_name}."${tb}" \
    ~{project_id}:~{snapshot_dataset}.${DEST_TABLE}

    # make a record of the table and the associated key for looking it back up
    bq --apilog=false --project_id=~{project_id} query --use_legacy_sql=false \
    "INSERT INTO ~{snapshot_dataset}.table_mappings (table_group, key, local_table_name, original_table_name, original_table_last_modified, is_not_modified) VALUES (\"~{run_name}\", \"~{retrieval_key}\", \"${DEST_TABLE}\", \"${tb}\", $LASTMODIFIED, false)"

    done


  >>>

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 500 HDD"
    preemptible: 3
    cpu: 1
  }

  output {
    Boolean done = true
  }
}


task RestoreSnapshotForRun {
  input {
    String project_id
    String dest_dataset
    String snapshot_dataset
    String run_name
    String start_retrieval_key
    String end_retrieval_key
    Boolean go = true
    String cloud_sdk_docker
  }
  meta {
    # because this is being used to determine if the data has changed, never use call cache
    volatile: true
  }

  String table_mappings_table = "table_mappings"

  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    # this hunk of sql will get the tables that we need to restore along with info about whether or not they are expected to change
    bq --apilog=false --project_id=gvs-internal query --use_legacy_sql=false --format=csv "WITH end_state AS ((SELECT original_table_name, local_table_name, is_not_modified FROM \`~{project_id}.~{snapshot_dataset}.table_mappings\` WHERE table_group = \"~{run_name}\" AND key = \"~{end_retrieval_key}\")) SELECT start_run.original_table_name, start_run.local_table_name, end_state.is_not_modified FROM \`~{project_id}.~{snapshot_dataset}.table_mappings\` as start_run join end_state ON start_run.original_table_name = end_state.original_table_name WHERE start_run.table_group = \"~{run_name}\" AND start_run.key = \"~{start_retrieval_key}\"" | \
    while IFS=',' read -r -a row; do
      # ignore the header
      if [ ${row[0]} == "original_table_name" ]; then
        continue
      fi

      echo "Processing entry ${row[@]}";

      original_table=${row[0]}
      local_table=${row[1]}
      is_not_modified=${row[2]}

      if [ $is_not_modified == "true" ]; then
        echo "restore table $original_table as a SNAPSHOT"
        bq cp \
        --project_id="~{project_id}" \
        --no_clobber \
        --snapshot=true \
        ~{project_id}:~{snapshot_dataset}."${local_table}" \
        ~{project_id}:~{dest_dataset}.${original_table}
      else
        echo "restore table $original_table as a CLONE"
        bq cp \
        --project_id="~{project_id}" \
        --no_clobber \
        --clone=true \
        ~{project_id}:~{snapshot_dataset}."${local_table}" \
        ~{project_id}:~{dest_dataset}.${original_table}
      fi
    done

  >>>

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 500 HDD"
    preemptible: 3
    cpu: 1
  }

  output {
    Boolean done = true
  }
}

task CheckResultsAgainstStoredState {
  input {
    String project_id
    String run_dataset
    String snapshot_dataset
    String run_name
    String comparison_key
    Boolean go = true
    String cloud_sdk_docker
  }
  meta {
    # because this is being used to determine if the data has changed, never use call cache
    volatile: true
  }

  String table_mappings_table = "table_mappings"
  String are_differences_present_file = "are_differences_present.txt"
  command <<<
    # Prepend date, time and pwd to xtrace log entries.
    PS4='\D{+%F %T} \w $ '
    set -o errexit -o nounset -o pipefail -o xtrace

    touch diffs.txt

    # Get all of the tables listed in the snapshot
    bq --apilog=false --project_id=gvs-internal query --use_legacy_sql=false --format=csv "SELECT original_table_name, local_table_name, is_not_modified FROM \`~{project_id}.~{snapshot_dataset}.table_mappings\` WHERE table_group = \"~{run_name}\" AND key = \"~{comparison_key}\"" | \
    while IFS=',' read -r -a row; do
      # ignore the header
      if [ ${row[0]} == "original_table_name" ]; then
        continue
      fi

#      echo "Checking table ${row[@]}";

      original_table=${row[0]}
      local_table=${row[1]}
      is_not_modified=${row[2]}

      # don't bother checking those that say they aren't modified. They were initialized from snapshots so the run would have failed during its run if they tried to modify them
      if [ $is_not_modified == "true" ]; then
        echo "This table isn't supposed to have been modified during this step, so we're aren't checking it (as the run would have failed if there was a modification attempt"
        continue
      else
        # Do the sql query that naively compares both tables.  Can't really intelligently detect when the data just changes, but will report them as missing/added
        echo "Checking $original_table to look for modifications"
        # this is where we COULD fork to either do a naive check like this or one where we have certain fields that we view as a key, so we can detect UPDATES as well.
        bq --apilog=false --project_id=gvs-internal query --use_legacy_sql=false --format=pretty "WITH StoredData AS ( SELECT truth_data as row_data, FARM_FINGERPRINT(FORMAT(\"%T\", truth_data)) as comparison_row_hash FROM \`~{project_id}.~{snapshot_dataset}.${local_table}\` as truth_data ), DataToValidate AS ( SELECT new_data as row_data, FARM_FINGERPRINT(FORMAT(\"%T\", new_data)) as comparison_row_hash FROM \`~{project_id}.~{run_dataset}.${original_table}\` as new_data ) SELECT IF(stored.comparison_row_hash IS NULL,\"Present in new data\",\"Missing from old data\") AS Change, IF(stored.comparison_row_hash IS NULL,new_data.row_data, stored.row_data).* FROM StoredData stored FULL OUTER JOIN DataToValidate new_data ON stored.comparison_row_hash = new_data.comparison_row_hash WHERE stored.comparison_row_hash IS NULL OR new_data.comparison_row_hash IS NULL LIMIT 100" > comparison.txt

        if [ -s comparison.txt  ]; then
          # uh oh.  This file is non-empty.
          echo "DIFFERENCES IN TABLE $original_table" >> diffs.txt
          cat comparison.txt >> diffs.txt
        fi
      fi
    done

    if [ -s diffs.txt ]; then
      # We've detected differences, sadly.
      echo "true" > ~{are_differences_present_file}
    else
      echo "false" > ~{are_differences_present_file}
    fi

  >>>

  runtime {
    docker: cloud_sdk_docker
    memory: "3 GB"
    disks: "local-disk 500 HDD"
    preemptible: 3
    cpu: 1
  }

  output {
    File differences = "diffs.txt"
    Boolean differences_present = read_boolean(are_differences_present_file)
    Boolean done = true
  }
}