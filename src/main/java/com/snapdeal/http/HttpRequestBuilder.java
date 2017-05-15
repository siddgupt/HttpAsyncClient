package com.snapdeal.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestBuilder {

	private String path;
	private Map<String, String> headers = new HashMap<String, String>();
	private ByteArrayOutputStream reqStream;
	
	
	public HttpRequestBuilder setRequestPath(String path){
		this.path = path;
		return this;
	}
	
	public HttpRequestBuilder setHeader(String name, String value){
		headers.put(name, value);
		return this;
	}
	
	public ByteBuffer buildGetReq() throws MalformedURLException{
		if(path == null)
			throw new MalformedURLException("Invalid Url: " + path);
		
		setHeader("Connection", "keep-alive");
		reqStream = new ByteArrayOutputStream();
		buildHeaders("GET ", reqStream);
		
		return ByteBuffer.wrap(reqStream.toByteArray());
	}
	
	public ByteBuffer buildPostReq(String body) throws MalformedURLException, IllegalAccessException{
		if(path == null)
			throw new MalformedURLException("Invalid Url: " + path);
		
		if(body == null)
			throw new IllegalAccessException("Invalid body: " + body);
		
		setHeader("Connection", "keep-alive");
		setHeader("Content-Type", "text/plain");
		byte[] bodybytes = body.getBytes();
		setHeader("Content-Length", ""+bodybytes.length);
		
		reqStream = new ByteArrayOutputStream();
		buildHeaders("POST ", reqStream);
		
		try {
			reqStream.write(bodybytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ByteBuffer.wrap(reqStream.toByteArray());
	}
	
	private void buildHeaders(String method, OutputStream ostr){
		PrintWriter wrtr = new PrintWriter(ostr);
		wrtr.write(method);
		wrtr.write(path);
		wrtr.println(" HTTP/1.1");
		for(String hdr : headers.keySet()){
			wrtr.print(hdr);
			wrtr.print(": ");
			wrtr.println(headers.get(hdr));
		}
		wrtr.println();
		wrtr.flush();
	}
}
