/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.cmdline.parser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.cmdline.tokenizer.TokenizerModelLoader;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public final class ParserTool extends BasicCmdLineTool {

  private static final Logger logger = LoggerFactory.getLogger(ParserTool.class);

  @Override
  public String getShortDescription() {
    return "Performs full syntactic parsing";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " [-bs n -ap n -k n -tk tok_model] model < sentences \n"
            + "-bs n: Use a beam size of n.\n"
            + "-ap f: Advance outcomes in with at least f% of the probability mass.\n"
            + "-k n: Show the top n parses. This will also display their log-probabilities.\n"
            + "-tk tok_model: Use the specified tokenizer model to tokenize the sentences. "
            + "Defaults to a WhitespaceTokenizer.";
  }

  private static final Pattern UNTOKENIZED_PAREN_PATTERN_1 = Pattern.compile("([^ ])([({)}])");
  private static final Pattern UNTOKENIZED_PAREN_PATTERN_2 = Pattern.compile("([({)}])([^ ])");

  public static Parse[] parseLine(String line, Parser parser, int numParses) {
    return parseLine( line, parser, WhitespaceTokenizer.INSTANCE, numParses );
  }

  public static Parse[] parseLine(String line, Parser parser, Tokenizer tokenizer, int numParses) {
    // fix some parens patterns
    line = UNTOKENIZED_PAREN_PATTERN_1.matcher(line).replaceAll("$1 $2");
    line = UNTOKENIZED_PAREN_PATTERN_2.matcher(line).replaceAll("$1 $2");

    // tokenize
    List<String> tokens = Arrays.asList( tokenizer.tokenize(line));
    String text = String.join(" ", tokens);

    Parse p = new Parse(text, new Span(0, text.length()), AbstractBottomUpParser.INC_NODE, 0, 0);
    int start = 0;
    int i = 0;
    for (Iterator<String> ti = tokens.iterator(); ti.hasNext(); i++) {
      String tok = ti.next();
      p.insert(new Parse(text, new Span(start, start + tok.length()), AbstractBottomUpParser.TOK_NODE, 0, i));
      start += tok.length() + 1;
    }
    Parse[] parses;
    if (numParses == 1) {
      parses = new Parse[]{parser.parse(p)};
    } else {
      parses = parser.parse(p, numParses);
    }
    return parses;
  }

  @Override
  public void run(String[] args) {

    if (args.length < 1) {
      logger.info(getHelp());
    } else {

      ParserModel model = new ParserModelLoader().load(new File(args[args.length - 1]));

      Integer beamSize = CmdLineUtil.getIntParameter("-bs", args);
      if (beamSize == null) {
        beamSize = AbstractBottomUpParser.defaultBeamSize;
      }

      Integer numParses = CmdLineUtil.getIntParameter("-k", args);
      boolean showTopK;
      if (numParses == null) {
        numParses = 1;
        showTopK = false;
      } else {
        showTopK = true;
      }

      Double advancePercentage = CmdLineUtil.getDoubleParameter("-ap", args);

      if (advancePercentage == null) {
        advancePercentage = AbstractBottomUpParser.defaultAdvancePercentage;
      }

      Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;
      String tokenizerModelName = CmdLineUtil.getParameter( "-tk", args );
      if (tokenizerModelName != null ) {
        TokenizerModel tokenizerModel = new TokenizerModelLoader().load(new File(tokenizerModelName));
        tokenizer = new TokenizerME( tokenizerModel );
      }

      Parser parser = ParserFactory.create(model, beamSize, advancePercentage);

      ObjectStream<String> lineStream;
      PerformanceMonitor perfMon = null;
      try {
        lineStream = new PlainTextByLineStream(new SystemInputStreamFactory(),
            SystemInputStreamFactory.encoding());
        perfMon = new PerformanceMonitor("sent");
        perfMon.start();
        String line;
        while ((line = lineStream.read()) != null) {
          if (line.trim().length() == 0) {
            logger.debug("empty line");
          } else {
            Parse[] parses = parseLine(line, parser, tokenizer, numParses);

            for (int pi = 0, pn = parses.length; pi < pn; pi++) {
              if (showTopK) {
                logger.debug("{} {} ", pi,  parses[pi].getProb());
              }

              parses[pi].show();

              perfMon.incrementCounter();
            }
          }
        }
      } catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
