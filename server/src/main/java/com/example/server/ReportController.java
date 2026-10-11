package com.example.server;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
@RestController public class ReportController {
 private final Map<String,String> reports=new ConcurrentHashMap<>();
 private static boolean valid(String given,String env){
  String expected=System.getenv(env);
  return expected!=null&&expected.length()>=24&&given!=null&&MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8));
 }
 private static void check(String token,String env){if(!valid(token,env))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 @PostMapping("/api/report")
 public Map<String,String> report(@RequestHeader("X-Agent-Token") String token,@RequestBody String body)throws Exception{
  check(token,"AGENT_TOKEN");if(body.length()>200000)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
  String id=node.path("deviceId").asText();
  if(!id.matches("[a-zA-Z0-9_-]{1,64}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  reports.put(id,body);return Map.of("status","received");
 }
 @GetMapping("/api/reports")
 public Map<String,String> reports(@RequestHeader("X-Viewer-Token") String token){
  check(token,"VIEWER_TOKEN");return Map.copyOf(reports);
 }
}