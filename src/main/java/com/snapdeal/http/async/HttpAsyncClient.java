package com.snapdeal.http.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;

import com.snapdeal.http.HttpRequestBuilder;
import com.snapdeal.http.HttpResponse;
import com.snapdeal.pool.ObjectPooler;
import com.snapdeal.pool.ObjectPooler.NoFreeResource;
import com.snapdeal.pool.ObjectPooler.ObjectFactory;

public class HttpAsyncClient {
	
	private ObjectPooler<AsynchronousSocketChannel> channelpool;
	
	private SocketAddress adrs;
	private String hostHeader;

	public HttpAsyncClient(String host, int port, int maxCon){
		this.hostHeader = host + ":" + port;
		this.adrs = new InetSocketAddress(host, port);
		
		ObjectFactory<AsynchronousSocketChannel> factory = new ObjectFactory<AsynchronousSocketChannel>() {
			
			public AsynchronousSocketChannel getNewInstance() {
				AsynchronousSocketChannel ch = null;
				try {
					ch = AsynchronousSocketChannel.open();
					Future<Void> conn = ch.connect(adrs);
					//conn.get(1000, TimeUnit.MILLISECONDS);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				return ch;
			}
			
			public void refresh(AsynchronousSocketChannel ch) {
				try {
					ch.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		
		this.channelpool = new ObjectPooler<AsynchronousSocketChannel>(maxCon, factory);
	}
	
	public HttpAsyncClient(String host, int port){
		this(host, port, 1);
	}
	
	public Future<HttpResponse> submitPostReq(String path, String body) throws NoFreeResource{
		try {			
			HttpRequestBuilder bldr = new HttpRequestBuilder();
			bldr.setRequestPath(path);
			bldr.setHeader("Host", hostHeader);			
			ByteBuffer buf = bldr.buildPostReq(body);
			System.out.println("Request len:" + buf.limit());
			
			RequestHandler hdlr = new RequestHandler(channelpool);
			boolean hasStreamResp = false;
			hdlr.process(buf, hasStreamResp);
			return hdlr;
		}  catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
	
	public Future<HttpResponse> submitGetReq(String path) throws NoFreeResource {
		try {			
			HttpRequestBuilder bldr = new HttpRequestBuilder();
			bldr.setRequestPath(path);
			bldr.setHeader("Host", hostHeader);			
			ByteBuffer buf = bldr.buildGetReq();
			System.out.println("Request len:" + buf.limit());
			
			RequestHandler hdlr = new RequestHandler(channelpool);
			boolean hasStreamResp = false;
			hdlr.process(buf, hasStreamResp);
			return hdlr;
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	public void close(){
		this.channelpool.close();
	}
}
