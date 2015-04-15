package util;

//VideoStream

import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.awt.ImageFrame;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class VideoStream {

	FileInputStream fis; // video file
	int frame_nb; // current frame nb
	private String filename;

	private List<MediaPicture> images;

	// -----------------------------------
	// constructor
	// -----------------------------------
	public VideoStream(String filename) throws Exception {

		this.filename = filename;

		// init variables
		fis = new FileInputStream(filename);
		frame_nb = 0;
	}

	// -----------------------------------
	// getnextframe
	// returns the next frame as an array of byte and the size of the frame
	// -----------------------------------
	public int getnextframe(byte[] frame) throws Exception {

		int length;
		String length_string;
		byte[] frame_length = new byte[5];

		// read current frame length
		fis.read(frame_length, 0, 5);

		// transform frame_length to integer
		length_string = new String(frame_length);

		length = Integer.parseInt(length_string);

		return (fis.read(frame, 0, length));
	}

	public void buildImages() throws InterruptedException, IOException {

		Demuxer demuxer = Demuxer.make();
		demuxer.open(filename, null, false, true, null, null);
		int numStreams = demuxer.getNumStreams();

		int videoStreamId = -1;
		Decoder videoDecoder = null;

		for (int i = 0; i < numStreams; i++) {

			final DemuxerStream stream = demuxer.getStream(i);
			final Decoder decoder = stream.getDecoder();

			if (decoder != null
					&& decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO) {
				videoStreamId = i;
				videoDecoder = decoder;
				break;
			}
		}

		if (videoStreamId == -1)
			throw new RuntimeException(
					"could not find video stream in container: " + filename);

		videoDecoder.open(null, null);

		final MediaPicture picture = MediaPicture.make(videoDecoder.getWidth(),
				videoDecoder.getHeight(), videoDecoder.getPixelFormat());

		final MediaPictureConverter converter = MediaPictureConverterFactory
				.createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24,
						picture);
		BufferedImage image = null;

		final ImageFrame window = ImageFrame.make();

		if (window == null) {
			throw new RuntimeException(
					"Attempting this demo on a headless machine, and that will not work. Sad day for you.");
		}

		

		final MediaPacket packet = MediaPacket.make();
		while (demuxer.read(packet) >= 0) {
			
			if (packet.getStreamIndex() == videoStreamId) {
				

				videoDecoder.decode(picture, packet, 0);
				if (picture.isComplete()) {
					
					image = converter.toImage(image, picture);
					
				}
			}
		}

		demuxer.close();

		
		window.dispose();
	}

	/**
	 * Takes the video picture and displays it at the right time.
	 */
	private static BufferedImage displayVideoAtCorrectTime(
			final MediaPicture picture, final MediaPictureConverter converter,
			BufferedImage image, final ImageFrame window)
			throws InterruptedException {

		// finally, convert the image from Humble format into Java images.
		image = converter.toImage(image, picture);
		// And ask the UI thread to repaint with the new image.

		window.setImage(image);

		return image;
	}

}
