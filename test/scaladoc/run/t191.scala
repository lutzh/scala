import scala.tools.nsc.doc.model._
import scala.tools.nsc.doc.base._
import scala.tools.nsc.doc.base.comment._
import scala.tools.nsc.doc.html.Page
import scala.tools.partest.ScaladocModelTest
import java.net.{URI, URL}
import java.io.File

object Test extends ScaladocModelTest {

  override def code =
    """
        /** See:
         *  - [[scala.collection.Map]] Simple linking
         *  - [[scala.collection.immutable.::]] Linking with symbolic name
         *  - [[scala.Int]].toLong Linking to a class
         *  - [[scala.Predef]] Linking to an object
         *  - [[scala.Int.toLong]] Linking to a method
         *  - [[scala]] Linking to a package
         *  - [[scala.AbstractMethodError]] Linking to a member in the package object
         *  - [[scala.Predef.String]] Linking to a member in an object
         *  - [[java.lang.Throwable]] Linking to a class in the JDK
         *
         *  Don't look at:
         *  - [[scala.NoLink]] Not linking :)
         */
        object Test {
          def foo(param: Any): Unit = {}
          def barr(l: scala.collection.immutable.List[Any]): Unit = {}
          def bar(l: List[String]): Unit = {}   // TODO: Should be able to link to type aliases
          def baz(d: java.util.Date): Unit = {} // Should not be resolved
        }
    """

  def scalaURL = "http://bog.us"
  val jdkURL = "http://java.us"
  val jvmDir   = if (scala.util.Properties.isJavaAtLeast(9)) "java.base/" else ""

  override def scaladocSettings = {
    val samplePath = getClass.getClassLoader.getResource("scala/Function1.class").getPath
    val scalaLibPath = if(samplePath.contains("!")) { // in scala-library.jar
      val scalaLibUri = samplePath.split("!")(0)
      new URI(scalaLibUri).getPath
    } else { // individual class files on disk
      samplePath.replace('\\', '/').dropRight("scala/Function1.class".length)
    }
    s"-no-link-warnings -doc-external-doc $scalaLibPath#$scalaURL -jdk-api-doc-base $jdkURL"
  }

  def testModel(rootPackage: Package): Unit = {
    import access._
    val test = rootPackage._object("Test")

    def check(memberDef: Def, expected: Int): Unit = {
      val externals = memberDef.valueParams(0)(0).resultType.refEntity collect {
        case (_, (LinkToExternalTpl(name, url, _), _)) =>
          assert(url.contains(scalaURL) || url.contains(jdkURL))
          name
      }
      assert(externals.size == expected)
    }

    check(test._method("foo"), 1)
    check(test._method("bar"), 0)
    check(test._method("barr"), 2)
    check(test._method("baz"), 1)

    val expectedUrls = collection.mutable.Set[String](
                         "scala/collection/Map",
                         "scala/collection/immutable/$colon$colon",
                         "scala/Int",
                         "scala/Predef$",
                         "scala/Int#toLong:Long",
                         "scala/index",
                         "scala/index#AbstractMethodError=AbstractMethodError",
                         "scala/Predef$#String=String"
                      ).map( _.split("#").toSeq ).map({
                        case Seq(one)      => scalaURL + "/" + one + ".html"
                        case Seq(one, two) => scalaURL + "/" + one + ".html#" + two
                        case x             => throw new MatchError(x)
                      }) ++ Set(s"$jdkURL/${jvmDir}java/lang/Throwable.html")

    def isExpectedExternalLink(l: EntityLink) = l.link match {
      case LinkToExternalTpl(name, baseUrlString, tpl: TemplateEntity) =>
        val baseUrl = new URI(Page.makeUrl(baseUrlString, Page.templateToPath(tpl)))
        val url = if (name.isEmpty) baseUrl
                  else new URI(baseUrl.getScheme, baseUrl.getSchemeSpecificPart, name)
        assert(expectedUrls.contains(url.toString), s"Expected $url in:${expectedUrls.map("\n  " + _).mkString}")
        true
      case _ => false
    }

    assert(countLinks(test.comment.get, isExpectedExternalLink) == 9,
            "${countLinks(test.comment.get, isExpectedExternalLink)} == 9")
  }
}
