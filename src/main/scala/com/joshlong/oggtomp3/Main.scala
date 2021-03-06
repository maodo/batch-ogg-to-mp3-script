package com.joshlong.oggtomp3


import scala.collection.immutable.List
import org.apache.commons.io.IOUtils
import java.io.{FileOutputStream, FileInputStream, File}
import java.lang.ProcessBuilder

/**
 * I want to get my music collection into iTunes.
 *
 * I use Linux (mostly), and encoded all my media in <CODE>.ogg</CODE> which offers superior sound. I want to get the into
 * iTunes so that my wife can take advantage of iTunes Match, which gives her the higher quality,
 * centrally stored version of the media, anyway. So, all i've got to do is get it to a format that
 * iTunes understands, which in this case is .mp3. After iTunes Match has recognized all of the
 * music, I'll delete the <CODE>.mp3</CODE>s locally.
 *
 * bleargh.
 *
 * @author Josh Long
 */
object Main extends App {

  // users are advised to specify these on using this
  val oggMusic = {
    val home = System.getenv("MUSIC_HOME") match {
      case null => "/Volumes/music"
      case dir: String => dir
    }
    assert(home != null, "you must specify which directory to scan for .ogg files. Consulted $MUSIC_HOME, and checked '/Volumes/music'")
    home
  }

  // directory on which my collection of .ogg files are stored
  val ffmpegBinary = {
    val ffmpegs = "/usr/local/bin/ffmpeg,/usr/bin/ffmpeg".split(",")
    val command = System.getenv("FFMPEG_CMD") match {
      case null => {
        val ffmpegSelection = ffmpegs.map(s => new File(s)).find(f => f.exists()).get
        if (ffmpegSelection != null) ffmpegSelection.getAbsolutePath else null
      }
      case cmd: String => cmd
    }
    assert(command != null, "we could not find an 'ffmpeg' command line. Either specify $FFMPEG_CMD, or ensure that /usr/local/bin/ffmpeg or /usr/bin/ffmpeg available.")
    command
  }

  def fileBaseName(file: File) =
    file.getName.substring(0, file.getName.lastIndexOf("."))

  def walkDirectories(directory: File, callback: (File) => Unit) {
    assert(directory != null && directory.exists())
    val files: List[File] = List[File]() ++ directory.listFiles
    callback(directory)
    files.filter(f2 => f2.isDirectory).foreach(f => walkDirectories(f, callback))
  }

  def escapePath(p: String) = {
    val sb = new StringBuilder
    var wasWS = false
    p.toCharArray.foreach(c => {
      if (Character.isWhitespace(c)) {
        if (wasWS) {
          // then there's no need to escape it, it's already been escaped
          sb.append("" + c)
        }
        else
          sb.append("\\" + c)
        wasWS = true
      } else {
        sb.append(c)
        wasWS = false
      }
    })
    sb.toString()
  }

  def copy(f: File, t: File) = {
    val ff = new FileInputStream(f)
    val of = new FileOutputStream(t)
    try {
      IOUtils.copyLarge(ff, of)
    } finally {
      IOUtils.closeQuietly(ff)
      IOUtils.closeQuietly(of)
    }
  }

  def isOgg(f: String) =
    f.toLowerCase.substring(1 + f.toLowerCase.lastIndexOf(".")).equals("ogg")

  println("ffmpeg binary chosen: " + ffmpegBinary )
  println("music home directory: " + oggMusic)

  val cmd = """ ffmpeg -ab 128 -i %s %s """.trim
  val musicFolder = new File(oggMusic)
  val desktop = new File(System.getProperty("user.home"), "Desktop")
  val staging = new File(desktop, "staging")
  val mirror = new File(desktop, "mp3s")
  Seq(mirror, staging).foreach(f => assert(f.exists() || f.mkdirs(), "the directory " + f.getAbsolutePath + " couldn't be created"))
  val stagingOgg = new File(staging, "tmp.ogg")
  val stagingMp3 = new File(staging, "tmp.mp3")

  walkDirectories(musicFolder, dir => {
    println("walking directory "+ dir.getAbsolutePath)
    val mirrorDir = new File(mirror, dir.getAbsolutePath.substring(musicFolder.getAbsolutePath.length()))
    assert(mirrorDir.exists() || mirrorDir.mkdirs(), "the directory " + mirrorDir.getAbsolutePath + " doesn't exist and couldn't be created")
    dir.listFiles.filter(f => f.isFile && isOgg(f.getName)).foreach(ogg => {
      val transcodedMp3File = new File(mirrorDir, fileBaseName(ogg) + ".mp3")
      if ( !transcodedMp3File.exists()) {
        println("transcoding "  + ogg.getAbsolutePath)
        assert(!stagingOgg.exists() || stagingOgg.delete())
        assert(!stagingMp3.exists() || stagingMp3.delete())
        copy(ogg, stagingOgg)
        val pb = new ProcessBuilder(ffmpegBinary, "-ab", "192k", "-i", stagingOgg.getAbsolutePath, stagingMp3.getAbsolutePath)
        pb.redirectErrorStream()
        val process = pb.start()
        assert(0 == process.waitFor(), "the process should have completed with exit code '0'")
        println(IOUtils.toString(process.getInputStream))
        println(IOUtils.toString(process.getErrorStream))
        copy(stagingMp3, transcodedMp3File)
        assert(stagingMp3.exists(), "the file " + stagingMp3.getAbsolutePath + " should exist")
      }
    })
  })
}
 