# Genomic Variant Store Beta Quickstart

In this Quickstart, you will learn how to use the Genomic Variant Store (GVS) in a [Terra workspace](https://app.terra.bio/#workspaces/gvs-prod/Genomic_Variant_Store_Beta) with provided example data.

The [GVS](../gvs-product-sheet.pdf) is a solution for variant discovery on a large scale developed by the Data Sciences Platform at the Broad Institute of MIT and Harvard.

The [GVS beta workspace](https://app.terra.bio/#workspaces/gvs-prod/Genomic_Variant_Store_Beta) contains a fully reproducible example workflow for variant discovery using the GVS workflow.

## Workflow Overview

![Diagram depicting the Genomic Variant Store workflow. Sample GVCF files are imported into the core data model. A filtering model is trained using Variant Extract-Train-Score, or VETS, and then applied while the samples are extracted as cohorts in sharded joint VCF files. Each step integrates BigQuery and GATK tools.](./genomic-variant-store_diagram.png)

The [GVS workflow](https://github.com/broadinstitute/gatk/blob/ah_var_store/scripts/variantstore/wdl/GvsJointVariantCalling.wdl) is an open-source, cloud-optimized workflow for joint calling at a large scale using the Terra platform. The workflow takes in single sample GVCF files with indices and produces sharded joint VCF files with indices, a manifest file, and metrics.

To learn more about the GVS workflow, see the [Genomic Variant Store workflow overview](./gvs-overview.md).

### What data does it require as input?

- Reblocked single sample GVCF files (`input_vcfs`)
- GVCF index files (`input_vcf_indexes`)

Example GVCF and index files in the Data tab of the [GVS beta workspace](https://app.terra.bio/#workspaces/gvs-prod/Genomic_Variant_Store_Beta) are hosted in a public Google bucket and links are provided in the sample data table.

While the GVS has been tested with 250,000 single sample whole genome GVCF files as input, only datasets of up to 25,000 whole genomes are being used for beta testing.

### What does it return as output?

The following files are stored in the workspace workflow execution bucket under Data>Files (within the left-hand menu on the "Data" workspace tab , under "Other Data", there is a "Files" link that allows you to navigate the files in the workspace bucket) or in the Google bucket specified in the inputs.

- Sharded joint VCF files, index files, the interval lists for each sharded VCF, and a list of the sample names included in the callset.
- Size of output VCF files in MB
- Manifest file containing the destinations and sizes in B of the output sharded joint VCF and index files

## Setup

You will need to set up several accounts and projects before you can begin testing the GVS workflow in the [GVS beta workspace](https://app.terra.bio/#workspaces/gvs-prod/Genomic_Variant_Store_Beta). To configure the prerequisites of the workflow, follow the instructions below.

For troubleshooting or questions, contact the [Broad Variants team](mailto:variants@broadinstitute.org).

### Step 1. Register for Terra

The GVS workflow requires a Terra data table to run properly.  If you are new to Terra, you’ll need to [register for a Terra account](https://support.terra.bio/hc/en-us/articles/360028235911).

If you already have a Terra account, you can skip this step.

### Step 2. Create a Terra billing project

While Terra is an open-access platform, it runs on the Google Cloud Platform (GCP), which means you’ll need to pay for storage and analyses you perform in the cloud using a billing project in Terra. For information on the cost of running the GVS workflow in the workspace, see the Time and cost estimates section below.

If you’ve never used GCP before, you are eligible to receive $300 in GCP credits, but unfortunately, you **can not** use these credits to run the GVS pipeline. Create a billing project in Terra by following the step-by-step instructions in the article [Set up billing with $300 Google credits to explore Terra](https://support.terra.bio/hc/en-us/articles/360046295092) but do not accept the invitation to use the free credits.

If you have used GCP before but need to create a billing project in Terra, follow steps 2 and 3 in the article [Set up billing with $300 Google credits to explore Terra](https://support.terra.bio/hc/en-us/articles/360046295092).

If you already have a Terra billing account that you would like to use to test the GVS workflow, you can skip this step.

### Step 3. Create a GCP project

To create a new GCP project for testing the GVS workflow, follow the instructions in the Google Cloud documentation article, [Creating and managing projects](https://cloud.google.com/resource-manager/docs/creating-managing-projects#creating_a_project).

When the project has been created successfully, you will see it appear in your list of projects.

If you already have a GCP project that you would like to use to test the GVS workflow, you can skip this step.

### Step 4. Create a BigQuery dataset

BigQuery datasets store the tables created during the execution of the GVS workflow. A new dataset should be created for each callset you want to create using the workflow. Samples can be added to BiqQuery datasets cumulatively and any samples in the dataset will be included in the extracted callset. 

Create a dataset in BigQuery inside the GCP project you created in Step 3 (above) by following the instructions in the Google Cloud documentation article, [Creating datasets](https://cloud.google.com/bigquery/docs/datasets#create-dataset).

Click on the arrow next to the name of your GCP project. When the BigQuery dataset has been created successfully, it will appear under the name of the GCP project.

### Step 5. Find your Terra proxy group

Proxy groups permit Terra to interface with GCP on your behalf, allowing you to run workflows on data in the cloud. Follow these steps to find your Terra proxy group.

1. Select the main menu (three horizontal line icon) at the top left of any Terra page. 
1. Click on your account name to open a dropdown menu.
1. Select Profile to open your profile in Terra. 
1. Find the Proxy Group field, which lists your proxy group underneath. 

For more information on proxy groups in Terra, see [Pet service accounts and proxy groups](https://support.terra.bio/hc/en-us/articles/360031023592). 

You will need your proxy group to grant Terra access to your GCP project and BigQuery dataset in the next step.

### Step 6. Configure permissions in GCP

Your Terra proxy group needs to be granted specific roles in GCP so Terra can create, read, and write to BigQuery tables as part of the GVS workflow.

Grant your proxy group the BigQuery Data Editor, BigQuery Job User, and BigQuery Read Session User roles on the Google Project that you created in Step 3 (above) that contains your BigQuery dataset. For step-by-step instructions, see the Google Cloud documentation article, [Manage access to projects, folders, and organizations](https://cloud.google.com/iam/docs/granting-changing-revoking-access#grant-single-role).

If you’ve done this correctly, you should see your Terra proxy group listed in the table of principals along with the roles you granted it.

### Step 7. Clone the GVS beta workspace

The GVS beta workspace in Terra is read-only, so you’ll need to clone the workspace to create a copy where you can upload your own data and run the workflow. Clone the workspace using the billing project you created in Step 2 (above) by following the instructions in the article [Make your own project workspace](https://support.terra.bio/hc/en-us/articles/360026130851).

## Running the workflow

The workflow in the GVS beta workspace is pre-configured to use the 10 sample GVCF files in the workspace Data tab.

The workflow is configured to call this input from the data table. To run:

1. Select the "GvsBeta" workflow from the Workflows tab.
1. Configure the workflow inputs.
    1. Enter a **name for the callset** as a string with the format “*CALLSET_NAME*” for the `call_set_identifier` variable. This string is used as to name several variables and files and should begin with a letter. Valid characters include A-z, 0-9, “.”, “,”, “-“, and “_”.
    1. Enter the name of your **BigQuery dataset** as a string with the format “*DATASET_NAME*” for the `dataset_name` variable.
    1. Enter the name of the **GCP project** that holds the BigQuery dataset as a string with the format “*PROJECT_NAME*” for the `project_id` variable.
    1. Enter the path of a **Google Cloud Storage directory for writing outputs**. Enter a string in the format "*gs://your_bucket/here*" in `extract_output_gcs_dir`. If you want the data in your Terra workspace bucket you can find that on the Workspace Dashboard, right panel, under Cloud Information. Copy the "Bucket Name" and use it to the inputs to create a gs path as a string like this "*gs://fc-338fe040-3522-484c-ba48-14b48f9950c2*". If you do not enter a bucket here, the outputs will be in the execution directory under *Files*.
1. **Save** the workflow configuration.
1. **Run** the workflow.

To run the GVS workflow on your own sample data, follow the instructions in the tutorial, [Upload data to Terra and run the GVS workflow](./run-your-own-samples.md).

### Time and cost
Below is an example of the time and cost of running the workflow with the sample data pre-loaded in the workspace.

| Number of Genome Samples | Elapsed Time (hh:mm) | Terra Cost $ | BigQuery Cost | Total Cost | Measured Cost per Sample |
|-------------------|----------------------|------------|---------------|------------|-----------------------------|
| 10                | 04:30                | $0.84      | $0.51         | $1.35      | $0.14                       |

Here is further information on the cost as you scale to run your dataset:

| Number of Genome Samples | Wall Clock Time (hh:mm) | Terra Cost $  | Measured Cost per Sample |
|-------------------|-----------------|---------|-----------------|
| 10                | 04:30        | $1.35   |  $0.14          |
| 1000              | 07:24        | $59.64     |  $0.06          |
| 2500              | 08:45        | $141.28     |  $0.06          |
| 5000              | 12:00        | $286.71  |  $0.06          |
| 10000             | 13:41        | $604.97 |  $0.06          |

**Note:** The time and cost listed above represent a single run of the GVS workflow. Actual time and cost may vary depending on BigQuery and Terra load at the time of the callset creation.

For more information about controlling Cloud costs, see [this article](https://support.terra.bio/hc/en-us/articles/360029748111).

#### Storage cost

The GVS workflow produces several intermediate files in your BigQuery dataset, and storing these files in the cloud will increase the storage cost associated with your callset. To reduce cloud storage costs, you can delete some of the intermediate files after your callset has been created successfully.

If you plan to create subcohorts of your data, you can delete the tables with `_REF_DATA`, `_SAMPLES`, and `_VET_DATA` at the end of the table name in your BigQuery dataset by following the instructions in the Google Cloud article, [Managing tables](https://cloud.google.com/bigquery/docs/managing-tables#deleting_a_table).

If you don’t plan to create subcohorts of your data, you can delete your BigQuery dataset by following the instructions in the Google Cloud article, [Managing datasets](https://cloud.google.com/bigquery/docs/managing-datasets#deleting_a_dataset). Note that the data will be deleted permanently from this location, but output files can still be found in the workspace bucket.

---

### Additional Resources
* For questions regarding GATK-related tools and Best Practices, see the [GATK website](https://gatk.broadinstitute.org/hc/en-us).
* For Terra-specific documentation and support, see the [Terra Support](https://support.terra.bio/hc/en-us).
* To learn more about the GATK Variant Extract-Train-Score (VETS) toolchain, see the [release notes](https://github.com/broadinstitute/gatk/blob/ah_var_store/scripts/variantstore/docs/release_notes/VETS_Release.pdf).

### Contact Information
* If you have questions or issues while running the GVS workflow in the GVS beta workspace, contact the [Broad Variants team](mailto:variants@broadinstitute.org).

* You can also Contact the Terra team for questions from the Terra main menu. When submitting a request, it can be helpful to include:
    * Your Project ID
    * Your workspace name
    * Your Bucket ID, Submission ID, and Workflow ID
    * Any relevant log information

### Citing the GVS workflow
If you use plan to publish data analyzed using the GVS workflow, please cite the [GVS beta workspace](https://app.terra.bio/#workspaces/gvs-prod/Genomic_Variant_Store_Beta).

Details on citing Terra workspaces can be found here: [How to cite Terra](https://support.terra.bio/hc/en-us/articles/360035343652)

Data Sciences Platform, Broad Institute (*Year, Month Day that the workspace was last modified*) gvs-prod/Genomic_Variant_Store_Beta [workspace] Retrieved *Month Day, Year that workspace was retrieved*, https://app.terra.bio/#workspaces/gvs-prod/Genomic_Variant_Store_Beta

### License
**Copyright Broad Institute, 2023 | Apache**
The workflow script is released under the Apache License, Version 2.0 (full license text at https://github.com/broadinstitute/gatk/blob/master/LICENSE.TXT). Note however that the programs called by the scripts may be subject to different licenses. Users are responsible for checking that they are authorized to run all programs before running these tools.