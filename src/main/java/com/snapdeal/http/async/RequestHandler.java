package com.snapdeal.http.async;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.snapdeal.http.HttpResponse;
import com.snapdeal.http.HttpResponse.ResponseParsingException;
import com.snapdeal.pool.ObjectPooler;
import com.snapdeal.pool.ObjectPooler.NoFreeResource;



public class RequestHandler implements CompletionHandler<Integer, AsynchronousSocketChannel>, Future<HttpResponse> {
	
	private static Logger log = LoggerFactory.getLogger(RequestHandler.class);
	
	private ResponseHandler respHandler = new ResponseHandler(this);
	
	private volatile RequestStatus status = RequestStatus.SENDING_REQ;
	
	private volatile boolean done = false;
		
	private Throwable exptn;
	
	private ObjectPooler<AsynchronousSocketChannel> channelpool;
	
	private boolean hasStreamResp = false;
	
	public RequestHandler(ObjectPooler<AsynchronousSocketChannel> pool){
		this.channelpool = pool;
	}
	
	public void process(ByteBuffer buf, boolean hasStreamResp) throws NoFreeResource{
		done = false;
		status = RequestStatus.SENDING_REQ;
		this.hasStreamResp = hasStreamResp;
		AsynchronousSocketChannel skt = getChannel();				
		skt.write(buf, skt, this);
	}
	
	public void completed(Integer numbytes, AsynchronousSocketChannel skt) {
		status = RequestStatus.WAITING_RESP;
		log.debug("Request sent: " + numbytes);
		skt.read(respHandler.buf, skt, respHandler);
	}

	public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
		status = RequestStatus.FAILED_REQ;
		exptn = exc;
		done = true;
		releaseChannel(attachment);
	}
	
	public RequestStatus getStatus(){
		return status;
	}
	
	public boolean isDone(){
		return done;
	}
		
	public boolean cancel(boolean arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	public HttpResponse get() throws InterruptedException, ExecutionException {
		while(!isDone())
			continue;
		
		if(isDone()) {
			switch(status){
			case COMPLETED : return getResp();
			case FAILED_REQ : 
			case FAILED_RESP :	throw new ExecutionException(exptn);			
			}
		}
		return null;
	}

	public HttpResponse get(long arg0, TimeUnit arg1) throws InterruptedException,
			ExecutionException, TimeoutException {
		// TODO Auto-generated method stub
		return get();
	}

	public boolean isCancelled() {
		// TODO Auto-generated method stub
		return false;
	}
	
	private HttpResponse getResp(){
		return respHandler.getResp();
	}
	
	private AsynchronousSocketChannel getChannel() throws NoFreeResource{
		AsynchronousSocketChannel ch = channelpool.acquire();
		try {
			while(ch.getRemoteAddress() == null){
				channelpool.release(ch);
				ch = channelpool.acquire();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ch;
	}
	
	private void releaseChannel(AsynchronousSocketChannel ch){
		channelpool.release(ch);
	}
	
	private void refreshChannel(AsynchronousSocketChannel ch){
		channelpool.refresh(ch);
	}
	
	public static enum RequestStatus {
		SENDING_REQ,
		WAITING_RESP,
		COMPLETED,
		FAILED_REQ,
		FAILED_RESP
	}
	
	private class ResponseHandler implements CompletionHandler<Integer, AsynchronousSocketChannel> {

		private volatile HttpResponse resp;
		
		private volatile ByteBuffer buf;

		private RequestHandler reqHdlr;
		
		public ResponseHandler(RequestHandler reqHdlr){
			this.reqHdlr = reqHdlr;
			buf = ByteBuffer.allocate(1);
			buf.clear();
		}
		
		public void completed(Integer numbytes, AsynchronousSocketChannel skt) {			
			log.debug("Response Obtained: " + numbytes);
						
			try{
				if(numbytes > 0) {
					BufferedInputStream bis = new BufferedInputStream(Channels.newInputStream(skt));
					resp = new HttpResponse(bis, buf, reqHdlr.hasStreamResp);
					
					switch(resp.getState()){
					case Completed_Resp: reqHdlr.releaseChannel(skt); break;
					case Pending_LastChunk: readLastChunk(skt, bis); break;
					}
				} else {
					// we are expecting some response but reached end of file
					throw new IOException("Unexpected EOF reached");
				}
				
				reqHdlr.status = RequestStatus.COMPLETED;
				
			} catch (IOException e) {
				reqHdlr.exptn = e;
				reqHdlr.status = RequestStatus.FAILED_RESP;
				// re-using channel not possible
				reqHdlr.refreshChannel(skt);
			} catch (ResponseParsingException e) {
				reqHdlr.exptn = e;
				reqHdlr.status = RequestStatus.FAILED_RESP;
				// we can re-use this channel
				reqHdlr.releaseChannel(skt);
			}
			
			reqHdlr.done = true;
		}

		public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
			reqHdlr.status = RequestStatus.FAILED_RESP;
			reqHdlr.exptn = exc;
			reqHdlr.done = true;
			reqHdlr.releaseChannel(attachment);
		}
		
		private void readLastChunk(final AsynchronousSocketChannel skt, BufferedInputStream bis) {
			byte[] buf = new byte[5];			
			try {
				int numread = bis.read(buf);
				if(isValidChunk(buf, numread)){
					reqHdlr.releaseChannel(skt);
				} else {
					reqHdlr.refreshChannel(skt);
				}
			} catch(IOException ex) {
				// we cannot re-use this channel but the response might be usable still
				reqHdlr.refreshChannel(skt);
			}
		}
		
		private boolean isValidChunk(byte[] buf, int bytes){
			if(bytes == 5){
				if(buf[0] == '0')
				if(buf[1] == '\r')
				if(buf[2] == '\n')
				if(buf[3] == '\r')
				if(buf[4] == '\n')
					return true;
			}
			
			return false;
		}
		
		public HttpResponse getResp(){
			return resp;
		}	

	}

}
