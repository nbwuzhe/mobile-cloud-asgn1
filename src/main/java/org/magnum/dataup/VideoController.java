package org.magnum.dataup;

import org.apache.commons.io.IOUtils;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;

import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;


@Controller
public class VideoController {
	
	private static final AtomicLong currentId = new AtomicLong(0L);
	
	private Map<Long, Video> videos = new HashMap<>();
	
	/**
	 * Get the list of video metadata.
	 * @return the list of video metadata.
	 */
	@ResponseBody
	@RequestMapping(value = "/video", method = RequestMethod.GET)
	public Collection<Video> getVideoList() {
		return videos.values();
	}
	
	/**
	 * Add metadata of a new video with a unique ID and set it's correct URL.
	 * @param video
	 * @return the Video object with correct ID and URL
	 */
	@ResponseBody
	@RequestMapping(value = "/video", method = RequestMethod.POST)
	public Video addVideo(@RequestBody Video video) {
		checkAndSetId(video);
		video.setDataUrl(getDataUrl(video.getId()));
		videos.put(video.getId(), video);
		return video;
	}
	
	/**
	 * Save the video (mp4 streaming) file. The metadata have to be already uploaded through method addVideo(Video video).
	 * If the corresponding metadata to the mp4 file is not existing, a 404 response will be thrown.
	 * @param videoId
	 * @param data
	 * @return the VideoStatus object describes whether the video streaming data (mp4) is correctly transmitted.
	 * @throws IOException
	 */
	@ResponseBody
	@RequestMapping(value = "/video/{id}/data", method = RequestMethod.POST)
	public VideoStatus setVideoData(@PathVariable("id") long videoId, @RequestParam("data") MultipartFile data) throws IOException {
		VideoStatus vs = new VideoStatus(VideoStatus.VideoState.PROCESSING);
		InputStream inStream = data.getInputStream();
		VideoFileManager vfm = VideoFileManager.get();
		if (videos.containsKey(videoId)) {
			try {
				vfm.saveVideoData(videos.get(videoId), inStream);
				inStream.close();
			} catch (Exception e) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file cannot be found");
			}
		} else  {
			throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "video not found"
            );
		}
		
		vs.setState(VideoStatus.VideoState.READY);
		return vs;
	}
	
	/**
	 * Get the video streaming (mp4) file
	 * @param videoId
	 * @param resp
	 * @return
	 * @throws IOException
	 */
	@ResponseBody 
	@RequestMapping(value = "/video/{id}/data", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getData(@PathVariable("id") long videoId, HttpServletResponse resp) throws IOException {
		if (videos.containsKey(videoId)) {
			try {
				OutputStream outStream = resp.getOutputStream();
				VideoFileManager vfm = VideoFileManager.get();
				vfm.copyVideoData(videos.get(videoId), outStream);
				outStream.flush();
				outStream.close();
				return new ResponseEntity<byte[]>(HttpStatus.OK);
				
			} catch (Exception e) {
				return new ResponseEntity<byte[]>(HttpStatus.NOT_FOUND);
			}
		} else {
			return new ResponseEntity<byte[]>(HttpStatus.NOT_FOUND);
		}
	}
	
	/**
	 * check if the video ID is valid. If not, increase the currentId by 1 and assign it to the video.
	 * @param v
	 */
	private void checkAndSetId(Video v) {
		if(v.getId() <= currentId.get()) {
			v.setId(currentId.incrementAndGet());
		}
	}
	
	/**
	 * Get full URL for a given video ID.
	 * @param videoId
	 * @return generated URL
	 */
	private String getDataUrl(long videoId){
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }

	/**
	 * Get the common part of URL for the current server.
	 * @return common part of URL
	 */
 	private String getUrlBaseForLocalServer() {
	   HttpServletRequest request = 
	       ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
	   String base = 
	      "http://"+request.getServerName() 
	      + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
	   return base;
	}
}
