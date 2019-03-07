package org.koagex.object.lending.sparkjava;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;

public class ImageResizer {

	public static byte[] resizeImage(byte[] image, int maxSize) {
		BufferedImage bufferedImage;
		ByteArrayOutputStream baos = null;
		try {
			bufferedImage = ImageIO.read(new ByteArrayInputStream(image));
			BufferedImage small = Scalr.resize(bufferedImage, maxSize);
			baos = new ByteArrayOutputStream();
			ImageIO.write(small, "jpg", baos);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		byte[] smallBytes = baos.toByteArray();
		return smallBytes;
	}

}
