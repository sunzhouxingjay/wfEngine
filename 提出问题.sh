#!/bin/bash
echo "POST invoke chaincode on peers of Org1 and Org2"
insName=$1
echo $(cat token.txt)
operation="提出问题"
question=$2
result=$(curl -s -X POST \
  http://localhost:4000/channels/workflowchannel/chaincodes/wfscc \
  -H "authorization: Bearer $(cat token.txt)" \
  -H "content-type: application/json" \
  -d "{
	\"peers\": [\"peer0.org1-workflow-com\"],
	\"fcn\":\"testcomplete\",
	\"args\":[\"{\\\"insName\\\":\\\"${insName}\\\",\\\"operation\\\":\\\"${operation}\\\",\\\"formVal\\\":\\\"{\\\\\\\"question\\\\\\\":\\\\\\\"${question}\\\\\\\"}\\\",\\\"business_data\\\":\\\"{\\\\\\\"question\\\\\\\":\\\\\\\"${question}\\\\\\\"}\\\",\\\"priString\\\":\\\"1\\\",\\\"cert\\\":\\\"2\\\",\\\"serviceArgs\\\":\\\"3\\\"}\"]
}")
echo $result
