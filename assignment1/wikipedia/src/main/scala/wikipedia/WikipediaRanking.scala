package wikipedia

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._

import org.apache.spark.rdd.RDD

case class WikipediaArticle(title: String, text: String)

object WikipediaRanking {

  val langs = List(
    "JavaScript", "Java", "PHP", "Python", "C#", "C++", "Ruby", "CSS",
    "Objective-C", "Perl", "Scala", "Haskell", "MATLAB", "Clojure", "Groovy")

  val conf: SparkConf = new SparkConf().setMaster("local").setAppName("wikipedia");
  val sc: SparkContext = new SparkContext(conf);
  // Hint: use a combination of `sc.textFile`, `WikipediaData.filePath` and `WikipediaData.parse`
  val wikiRdd: RDD[WikipediaArticle] =  sc.textFile(WikipediaData.filePath, 1)
     .map { x => WikipediaData.parse(x) };
   

  /** Returns the number of articles on which the language `lang` occurs.
   *  Hint1: consider using method `aggregate` on RDD[T].
   *  Hint2: should you count the "Java" language when you see "JavaScript"?
   *  Hint3: the only whitespaces are blanks " "
   *  Hint4: no need to search in the title :)
   */
  def occurrencesOfLang(lang: String, rdd: RDD[WikipediaArticle]): Int ={
  rdd.aggregate(0)((count, article)=> if(article.text.split(" ").contains(lang)) count+1 else count,
    (x, y)=> (x + y))
  }

  /* (1) Use `occurrencesOfLang` to compute the ranking of the languages
   *     (`val langs`) by determining the number of Wikipedia articles that
   *     mention each language at least once. Don't forget to sort the
   *     languages by their occurrence, in decreasing order!
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangs(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = 
    langs.map { x => (x,occurrencesOfLang(x,rdd)) }.sortBy(x=>x._2).reverse;

  /* Compute an inverted index of the set of articles, mapping each language
   * to the Wikipedia pages in which it occurs.
   */
  def makeIndex(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])] = {
//    def occur(lang:String,rdd:RDD[WikipediaArticle])={
//          val pattern = (".*[ ]*"+lang.toLowerCase()+"[^s]*[ ]*.*");
//      rdd.filter { x=>x.text.toLowerCase().matches(pattern) };
//      
//    }
//  
//     sc.parallelize(langs.map { x => (x,occur(x,rdd).collect().toIterable) });
    rdd.flatMap(article =>
    langs
      .filter(lang => article.text.split(" ").contains(lang))
      .map(_ -> article)
  ).groupByKey
  }

  /* (2) Compute the language ranking again, but now using the inverted index. Can you notice
   *     a performance improvement?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsUsingIndex(index: RDD[(String, Iterable[WikipediaArticle])]): List[(String, Int)] ={
   index.mapValues(articles => articles.size)
    .sortBy(_._2, ascending = false)
    .collect().toList
  }

  /* (3) Use `reduceByKey` so that the computation of the index and the ranking are combined.
   *     Can you notice an improvement in performance compared to measuring *both* the computation of the index
   *     and the computation of the ranking? If so, can you think of a reason?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsReduceByKey(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = {
         
//    def findPair(lang:String,rdd:RDD[WikipediaArticle]) = rdd.map { x => (lang,if(x.text.toLowerCase().matches(".*[ ]*"+lang.toLowerCase()+"[^s]*[ ]*.*"))1 else 0) };
//    val a = sc.parallelize(langs.map { x =>findPair(x,rdd).collect() },1);
//    val b = a.flatMap(x=>x);
//    val c = b.reduceByKey(_+_).sortBy(x=>x._2).collect().reverse;
//    return c.toList;
    rdd.flatMap(
    article => langs.filter(article.text.split(" ").contains).map((_, 1))
  ) .reduceByKey(_+_)
    .sortBy(_._2, ascending = false)
    .collect().toList
    
  }
    

  def main(args: Array[String]) {

    /* Languages ranked according to (1) */
    val langsRanked: List[(String, Int)] = timed("Part 1: naive ranking", rankLangs(langs, wikiRdd))

    /* An inverted index mapping languages to wikipedia pages on which they appear */
    def index: RDD[(String, Iterable[WikipediaArticle])] = makeIndex(langs, wikiRdd)

    /* Languages ranked according to (2), using the inverted index */
    val langsRanked2: List[(String, Int)] = timed("Part 2: ranking using inverted index", rankLangsUsingIndex(index))

    /* Languages ranked according to (3) */
    val langsRanked3: List[(String, Int)] = timed("Part 3: ranking using reduceByKey", rankLangsReduceByKey(langs, wikiRdd))

    /* Output the speed of each ranking */
    println(timing)
    sc.stop()
  }

  val timing = new StringBuffer
  def timed[T](label: String, code: => T): T = {
    val start = System.currentTimeMillis()
    val result = code
    val stop = System.currentTimeMillis()
    timing.append(s"Processing $label took ${stop - start} ms.\n")
    result
  }
}
