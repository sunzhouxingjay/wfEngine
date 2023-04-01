package com.wq.wfEngine.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.wq.wfEngine.tool.simpleJson.simpleJsonParser;

public class inputSelectParser {


    @SuppressWarnings("unchecked")
    public static String parse(Map<String,Object> root,String sentence) {
        Map<String,Object> mapping=(Map<String,Object>)simpleJsonParser.parse(sentence);
        Map<String,Object> result=new HashMap<>();
        int index=-1;
        
        for (String name:mapping.keySet()) {
            String[] paths=String.valueOf(mapping.get(name)).split("\\.");
            Object pointer =root;
            List<Object> list=null;
            List<String> listPath=new ArrayList<>();
            for (int i=0;i<paths.length;++i) {
                if (paths[i].charAt(0)=='$') {
                    index=Integer.valueOf(paths[i].substring(1)).intValue();
                    if (i!=paths.length-1) throw new RuntimeException("暂时不支持这种");
                    continue;
                }
                if (pointer==null) {
                    break;
                } else if (pointer instanceof Map) {
                    pointer=((Map<String,Object>)pointer).get(paths[i]);
                } else if (pointer instanceof List) {
                    list=(List<Object>)pointer;
                    if (list.isEmpty()) {
                        pointer=null;
                    } else {
                        pointer=((Map<String,Object>)list.get(0)).get(paths[i]);
                        listPath.add(paths[i]);
                    }
                } else {
                    pointer=null;
                }
            }
            if (pointer!=null) {
                if (list!=null) {
                    List<Object> l=new ArrayList<>();
                    for (Object object:list) {
                        for (String str:listPath) {
                            object=((Map<String,Object>)object).get(str);
                        }
                        l.add(object);
                    }
                    result.put(name,l);
                } else {
                    result.put(name,pointer.toString());
                }

            } else {
                result.put(name,null);
            }
            if (index!=-1) {
                List<Object> resList=(List<Object>)result.get(name);
                result.put(name,resList.get(index));
            }
        }
        return jsonTransfer.mapToJsonString(result);


        // jsonTreeNode dataRoot=new jsonTreeNode(null);
        // //初始数据的map
        // Queue<jsonTreeNode> queue=new LinkedList<>();
        // queue.add(new jsonTreeNode(businessData));
        // //层序遍历，将多层json解析
        // while (!queue.isEmpty()) {
        //     jsonTreeNode node=queue.poll();
        //     if (!dataRoot.sons.containsKey("init")) {
        //         dataRoot.sons.put("init",node);
        //     }
        //     if (node.jsonStr.charAt(0)=='{') {
        //         Map<String,Object> temp=jsonTransfer.jsonToMap(node.jsonStr);
        //         if (temp!=null) {
        //             for (String key:temp.keySet()) {
        //                 //增加node的sons
        //                 node.sons.put(key,new jsonTreeNode(String.valueOf(temp.get(key))));
        //                 //将初始化后的son加入队列
        //                 queue.add(node.sons.get(key));
        //             }
        //         } 
        //     }
        // }
        // queue.clear();

        // //上一个serviceTask的返回值
        // queue.add(new jsonTreeNode(lastServiceResponse));
        // //层序遍历，将多层json解析
        // while (!queue.isEmpty()) {
        //     jsonTreeNode node=queue.poll();
        //     if (!dataRoot.sons.containsKey("pre")) {
        //         dataRoot.sons.put("pre",node);
        //     }
        //     if (node.jsonStr.charAt(0)=='{') {
        //         Map<String,Object> temp=jsonTransfer.jsonToMap(node.jsonStr);
        //         if (temp!=null) {
        //             for (String key:temp.keySet()) {
        //                 //增加node的sons
        //                 node.sons.put(key,new jsonTreeNode(String.valueOf(temp.get(key))));
        //                 //将初始化后的son加入队列
        //                 queue.add(node.sons.get(key));
        //             }
        //         } 
        //     }
        // }
        // queue.clear();
        // //映射规则，变量名-指定值
        // Map<String,Object> mapping=jsonTransfer.jsonToMap(sentence);
        // Map<String,Object> result=new HashMap<>();
        
        // for (String name:mapping.keySet()) {
        //     String[] paths=String.valueOf(mapping.get(name)).split("\\.");
        //     jsonTreeNode pointer =dataRoot;
        //     for (int i=0;i<paths.length;++i) {
        //         pointer=pointer.sons.get(paths[i]);
        //         if (pointer==null) {
        //             break;
        //         }
        //     }
        //     if (pointer!=null) {
        //         result.put(name,pointer.jsonStr);
        //     } else {
        //         result.put(name,null);
        //     }
        // }
        // return jsonTransfer.mapToJsonString(result);
    }
}
