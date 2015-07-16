""" Merges health data records by id """

import json

inputlist = [
    'health-data-staging-0501-0706',
    'health-data-staging-0706',
    'health-data-staging-0707-0710'
]

recordlist = []
idset = set()
for inputfile in inputlist:
    with open(inputfile, 'r') as lines:
        records = [json.loads(line) for line in lines]
        for record in records:
            if record['id'] not in idset:
                recordlist.append(record)
                idset.add(record['id'])

output = 'health-data-staging-merged'
with open(output, 'w') as outputfile:
    for record in recordlist:
        outputfile.write(json.dumps(record))
        outputfile.write('\n')
