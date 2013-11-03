import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InvalidFormatException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.QueueingConsumer;


public class TokenizerServer implements Runnable {

	private static final String RPC_QUEUE_NAME = "token_task_queue";
	private String rabbitHost;
	
	public TokenizerServer() {
		this.rabbitHost = "localhost";
	}
	
	public TokenizerServer(String rabbitHost) {
		this.rabbitHost = rabbitHost;
	}
		
	@Override
	public void run() {
		// RabbitMQ setup
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(this.rabbitHost);
		Connection connection = null;
		try{
			connection = factory.newConnection();
			Channel channel = connection.createChannel();
	
			channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);	//durable, exclusive, autodelete, other property; exclusive might be needed for client, but not for server
			channel.basicQos(1);
			
			QueueingConsumer consumer = new QueueingConsumer(channel);
			channel.basicConsume(RPC_QUEUE_NAME, false, consumer);

			System.out.println(" [x] Awaiting RPC requests");
			
			//Listen
			while (true) {
			    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
			    
			    BasicProperties props = delivery.getProperties();
			    BasicProperties replyProps = new BasicProperties.Builder().correlationId(props.getCorrelationId()).build();

			    String message = new String(delivery.getBody());
			    boolean success = false;
			    
			    //Call worker to do the work.
			    System.out.println("Retrieve message: " + message );
			    String response = null;
			    try {
					response = this.worker(message);
					success = true;
				} catch (Exception e) {
					e.printStackTrace();
				}
			    
			    if(success){
			    	//Send back the response
			    	System.out.println("[x] Done");
		    		channel.basicPublish("", props.getReplyTo(), replyProps, response.getBytes());
			    }
			    else{
			    	 //Put back?
                    channel.basicPublish("", RPC_QUEUE_NAME,
                            MessageProperties.PERSISTENT_TEXT_PLAIN,
                            message.getBytes());

                    //sleep for 10 sec, if failed.
                    System.out.println("[!] Failed, put back!");
                    Thread.sleep(10000);
 
                }
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Worker function
	 * @param message
	 * 		  format: {"orig_text":"xxx"}
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws InvalidFormatException 
	 */
	private String worker(String message) throws ParseException, InvalidFormatException, IOException {
		
		if(message==null || message.length()==0){
			return new JSONObject().toJSONString();
		}
		
		JSONObject json = (JSONObject)new JSONParser().parse(message);
		String orig = (String) json.get("orig_text");
	    System.out.println("\tOriginal=" + orig);
	    
	    
	    //Tokenize
	    InputStream modelIn = new FileInputStream("opennlp-model/en-sent.bin");
		SentenceModel model = new SentenceModel(modelIn);
		SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
		InputStream modelInTok = new FileInputStream("opennlp-model/en-token.bin");
		TokenizerModel modelTok = new TokenizerModel(modelInTok);
		Tokenizer tokenizer = new TokenizerME(modelTok);

		String sentences[] = sentenceDetector.sentDetect(orig);
		if(sentences.length > 1){
			System.out.println("[!] More than one sentence?!");
			for(String s:sentences){
				System.out.println("[!]\t"+s);
			}
			System.out.println("[!]End of Sentences");
		}
		
		JSONArray retTokens = new JSONArray();
		for (String x : sentences) {
			String tokens[] = tokenizer.tokenize(x);
			for(String y : tokens){
				retTokens.add(y);
			}
			
		}
		
		JSONObject retJson = new JSONObject();
		retJson.put("orig_text", orig);
		retJson.put("tokens", retTokens);
		retJson.put("length", retTokens.size());
		
		return retJson.toJSONString();
		
	}
	
	public static void main(String[] args) {
		TokenizerServer ts = new TokenizerServer();
		ts.run();
	}
	

}
