package com.snapdeal.http.async.test;


import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Test;

import com.snapdeal.http.HttpResponse;
import com.snapdeal.http.HttpResponse.ResponseParsingException;
import com.snapdeal.http.async.HttpAsyncClient;
import com.snapdeal.pool.ObjectPooler.NoFreeResource;

public class HttpAsyncClientTest {

	@Test
	public void testSubmitPostReq() throws ResponseParsingException {
		String path = "/RestCalculator/calc/upload";
        StringBuilder bldr = new StringBuilder(2010);
        for(int i = 0; i < 20000; i++){
        	bldr.append('a');
        }
        
		String host = "localhost";
        int port = 8080;
        int maxConn = 2;
        HttpAsyncClient client = new HttpAsyncClient(host, port, maxConn);
                
        Map<Future<HttpResponse>, Date> respList = new HashMap<Future<HttpResponse>, Date>();
                
        for(int i = 0; i < maxConn; i++){
        	try {
        		Date start = new Date();
        		Future<HttpResponse> resp = client.submitPostReq(path, bldr.toString());
    			Date end = new Date();
    			System.out.println("Scheduled Request " + i + " : " + resp.hashCode() + " in ms : " + (end.getTime() - start.getTime()));
    			respList.put(resp, start);
    		} catch (NoFreeResource e) {    			
    			e.printStackTrace();
    		}
        }
        
        
        while(respList.size() > 0) {
        	Iterator<Future<HttpResponse>> itr = respList.keySet().iterator();
	        while(itr.hasNext()){
	        	Future<HttpResponse> f = itr.next();
	        	if(f.isDone()){
	        		try {
						HttpResponse resp = f.get();
						Date end = new Date();
						long elapsed = end.getTime() - respList.get(f).getTime();
						System.out.println("Message from resp : " + f.hashCode() + " after time : " + elapsed + " - " +  resp.getObject(RestMsg.class).getMsg());
						itr.remove();
						//i--;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	        	}
	        }
        }
        
        client.close();
	}

}
