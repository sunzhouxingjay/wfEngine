#!/bin/bash
fileContent=$(cat ./VipChannel.bpmn)
result=$(curl -s -X POST \
  http://localhost:8080/testwfDeployment \
  -H "content-type: application/json" \
  -d "{\"fileName\":\"file:VipChannel.bpmn\",\"fileContent\":${fileContent}}")
  
echo $result
