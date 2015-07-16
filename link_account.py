""" Links staging accounts with production accounts by matching emails """

import json

rlist = []
with open('stormpath-account-uat', 'r') as lines:
    rlist = [json.loads(line) for line in lines]

rsdict = {r['email'].lower(): r for r in rlist}

with open('stormpath-account-prod', 'r') as lines:
    rlist = [json.loads(line) for line in lines]

for rp in rlist:
    if rp['email'].lower() in rsdict:
        rs = rsdict[rp['email'].lower()]
        rs['p_email'] = rp['email']
        rs['p_id'] = rp['id']
        rs['p_healthId'] = rp['healthId']

with open('stormpath-account-linked', 'w') as output:
    for rs in rsdict.values():
        if 'p_id' in rs:
            output.write(json.dumps(rs))
            output.write('\n')

with open('stormpath-account-not-linked', 'w') as output:
    for rs in rsdict.values():
        if 'p_id' not in rs:
            output.write(json.dumps(rs))
            output.write('\n')
