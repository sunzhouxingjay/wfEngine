#!/bin/bash
result=$(curl -s -X POST \
  http://localhost:8080/testwfInstance \
  -H "content-type: application/json" \
  -d "{\"fileName\":\"file:VipChannel.bpmn\",\"insName\":\"ins:VipChannel.bpmn\"}")
  
echo $result
