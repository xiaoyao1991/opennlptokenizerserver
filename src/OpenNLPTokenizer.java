import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public class OpenNLPTokenizer {

	public static void token(String art) {
		try {
			InputStream modelIn = new FileInputStream("opennlp-model/en-sent.bin");

			SentenceModel model = new SentenceModel(modelIn);

			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);

			InputStream modelInTok = new FileInputStream("opennlp-model/en-token.bin");

			TokenizerModel modelTok = new TokenizerModel(modelInTok);

			Tokenizer tokenizer = new TokenizerME(modelTok);

			String sentences[] = sentenceDetector.sentDetect(art);
			for (String x : sentences) {
				String tokens[] = tokenizer.tokenize(x);
				for (String y : tokens) {
					System.out.println(y);
				}

			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
//		String sentence = "";
//		for(String arg:args){
//			sentence += arg + " ";
//		}
		token(args[0]);
	}
}
