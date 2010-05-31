#!/usr/bin/env groovy

//script requires Groovy, Markdown and Calibre. On Ubuntu call "sudo apt-get install groovy markdown calibre"

/*Class TextDumper is required for Groovy < 1.7*/
class TextDumper implements Runnable {
    InputStream input;
    StringBuffer sb;
    
    TextDumper(input, sb) {
      this.input = input;
      this.sb = sb;
    }

    void run() {
	def isr = new InputStreamReader(input);
	def br = new BufferedReader(isr);
	String next;
	try {
	    while ((next = br.readLine()) != null) {
		sb.append(next);
		sb.append("\n");
	    }
	} catch (IOException e) {
	    throw new GroovyRuntimeException("exception while reading process stream", e);
	}
    }
}
println "This script requires Groovy, Markdown and Calibre. On Ubuntu call 'sudo apt-get install groovy markdown calibre'"
if (args.size() == 0) {
  println "First parameter must be name of language(=directory). For example: 'en'"
  return
}
def lang = args[0]
def langDir = new File("..", lang)
def figures = new File("../figures")
def dest = new File("target")

def env = System.getenv()
def MARKDOWN_BIN = env['MARKDOWN_BIN']
if( MARKDOWN_BIN == null ) {
    MARKDOWN_BIN = "markdown"
}
def EBOOK_CONVERT_BIN = env['EBOOK_CONVERT_BIN']
if( EBOOK_CONVERT_BIN != null ) {
    EBOOK_CONVERT_BIN = "ebook-convert"
}

dest.deleteDir()
dest.mkdirs()
def markdownFile = ~/.*\.markdown/
def filesForIndex = []
langDir.eachDir { dir -> 
  dir.eachFileMatch(markdownFile) { file ->
    def preparedFile  = new File(dest, file.getName())
    println "Processing ${file}"
    preparedFile.withWriter('UTF-8') { writer ->
      file.withReader('UTF-8') { reader -> 
	String line = null
	while ( (line = reader.readLine()) != null) {
	  def matches = line =~ /^\s*Insert\s(.*)/
	  if (matches.size() != 0) {
	    def imageFile = matches[0][1].trim().replaceAll(/(.*)(\.png)/, '$1-tn$2')
	    //println "file:" + imageFile
	    def nextLine = reader.readLine()
	    line = """![${nextLine}](../../figures/${imageFile} "${nextLine}")"""
	    //println "Line: " + line 
	  }
	  writer.print(line)
	  writer.print("\n")
	} //while
      } // reader
    } //writer
    def htmlFile = new File(dest, file.getName() + ".html")
    filesForIndex += htmlFile;
    def cmd = [MARKDOWN_BIN, """${preparedFile}"""]
    println "Executing ${cmd}"
    def proc = cmd.execute();
    def sout = new StringBuffer()
    def serr = new StringBuffer()
    def groovyLessThan165 = {
	Thread thread = new Thread(new TextDumper(proc.getInputStream(), sout));
	thread.start();
        try { thread.join(); } catch (InterruptedException ignore) {}
        try { proc.waitFor(); } catch (InterruptedException ignore) {}
    }
    groovyLessThan165()
    //Groovy >= 1.6.5: proc.waitForProcessOutput(sour, serr)
    htmlFile.withWriter {
      it << sout.toString()
    }
  } //file
} //dir 
def indexFile = new File(dest, "progit.html")
indexFile.withWriter('UTF-8') { writer -> 
  writer << """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Pro Git - professional version control</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" /></head><body>"""
  filesForIndex.sort().each { file ->
    def fileName = file.getName()
    file.eachLine {
      writer << it
      writer << '\n'
    }
  }
  writer << """</body></html>"""
}
def epubFile = new File(dest, "progit_${args[0]}.epub")
println "EpubFile" + epubFile
// for more options see:
// http://calibre-ebook.com/user_manual/cli/ebook-convert-3.html#html-input-to-epub-output
def cmd = ["""ebook-convert""", indexFile.getPath(), epubFile.getPath(), 
  """--cover""", """title.png""", 
  """--authors""", "Scott Chacon", 
  "--isbn", "1430218339",
  "--publisher", "Apress",
  "--pubdate", "Aug 2009",
  "--rating", "4",
  "--comments", "licensed under the Creative Commons Attribution-Non Commercial-Share Alike 3.0 license", 
  "--no-chapters-in-toc",
  "--toc-threshold", "15",
  "--level1-toc", "//h:h1", 
  "--extra-css", 'pre { white-space: pre-wrap; }',
]
def proc = cmd.execute();
proc.waitFor();


