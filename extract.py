""" Extracts, dedupes, and outputs data to lines """

import json

inputfile = 'health-data-staging-merged'
outputfile = 'upload-list-uat'
field = 'uploadId'

hcset = set()
with open(inputfile, 'r') as lines:
    records = [json.loads(line) for line in lines]
    for record in records:
        hcset.add(record[field])

with open(outputfile, 'w') as outputfile:
    for hc in hcset:
        outputfile.write(hc)
        outputfile.write('\n')
