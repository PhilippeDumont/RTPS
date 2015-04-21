package util;

//VideoStream

import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoStream {

	FileInputStream fis; // video file
	int frame_nb; // current frame nb
	private String filename;
	public int currentFrame;

	private List<BufferedImage> frames;

	public VideoStream(String filename) throws Exception {

		this.filename = filename;

		// init variables
		// fis = new FileInputStream(filename);
		frame_nb = 0;

		frames = new ArrayList<BufferedImage>();
		currentFrame = 0;

		buildFrames();
	}

	public BufferedImage getNextImage() {
		BufferedImage image = frames.get(currentFrame);
		currentFrame++;

		return image;

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

		// transform frame_length to integer, who is the size of the picture
		length_string = new String(frame_length);

		length = Integer.parseInt(length_string);

		return (fis.read(frame, 0, length));
	}

	/**
	 * Create an List with all the frame content in the video file.
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void buildFrames() throws InterruptedException, IOException {

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

		if (videoStreamId == -1) {
			throw new RuntimeException(
					"could not find video stream in container: " + filename);
		}

		videoDecoder.open(null, null);

		final MediaPicture picture = MediaPicture.make(videoDecoder.getWidth(),
				videoDecoder.getHeight(), videoDecoder.getPixelFormat());

		final MediaPictureConverter converter = MediaPictureConverterFactory
				.createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24,
						picture);

		BufferedImage image = null;

		final MediaPacket packet = MediaPacket.make();
		while (demuxer.read(packet) >= 0) {

			if (packet.getStreamIndex() == videoStreamId) {

				videoDecoder.decode(picture, packet, 0);
				if (picture.isComplete()) {

					BufferedImage var;
					var = converter.toImage(image, picture);
					this.frames.add(var);

				}
			}
		}

		demuxer.close();
	}

}
