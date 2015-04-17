package util;

//class RTPpacket

import static java.lang.System.arraycopy;

public class RTPpacket {

	/**
	 * size of the RTP header
	 */
	static int HEADER_SIZE = 12;

	public int Version;
	public int Padding;
	public int Extension;
	public int CC;
	public int Marker;
	public int PayloadType;
	public int SequenceNumber;
	public int TimeStamp;
	public int Ssrc;

	// Bitstream of the RTP header
	public byte[] header = new byte[HEADER_SIZE];

	// size of the RTP payload
	public int payload_size;

	// Bitstream of the RTP payload
	public byte[] payload;

	/**
	 * Construction of a packet to send
	 * 
	 * @param PType
	 * @param Framenb
	 * @param Time
	 * @param data
	 * @param data_length
	 */
	public RTPpacket(int PType, int Framenb, int Time, byte[] data,
			int data_length) {

		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		PayloadType = PType;
		SequenceNumber = Framenb;
		TimeStamp = Time;

		header[0] = (byte) (header[0] | Version << (7));
		header[1] = (byte) (PayloadType & 0x7F);
		header[2] = (byte) (SequenceNumber >> 8);
		header[3] = (byte) (SequenceNumber & 0xFF);
		header[6] = (byte) (TimeStamp >> 8);
		header[7] = (byte) (TimeStamp & 0xFF);
		header[10] = (byte) (Ssrc >> 8);
		header[11] = (byte) (Ssrc & 0xFF);

		payload_size = data_length;
		payload = new byte[data_length];

		arraycopy(data, 0, payload, 0, data_length);

	}

	/**
	 * Construction of a packet to receive
	 * 
	 * @param packet
	 * @param packet_size
	 */
	public RTPpacket(byte[] packet, int packet_size) {

		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		// check if total packet size is lower than the header size
		if (packet_size >= HEADER_SIZE) {

			arraycopy(packet, 0, header, 0, HEADER_SIZE);

			// get the payload bitstream:
			payload_size = packet_size - HEADER_SIZE;
			payload = new byte[payload_size];
			arraycopy(packet, HEADER_SIZE, payload, HEADER_SIZE - HEADER_SIZE,
					packet_size - HEADER_SIZE);

			// interpret the changing fields of the header:
			PayloadType = header[1] & 127;

			SequenceNumber = unsigned_int(header[3]) + 256
					* unsigned_int(header[2]);

			TimeStamp = unsigned_int(header[7]) + 256 * unsigned_int(header[6])
					+ 65536 * unsigned_int(header[5]) + 16777216
					* unsigned_int(header[4]);
		} // Error
	}

	public int unsigned_int(byte data) {
		return data;
	}

	/**
	 * 
	 * @param data
	 * @return the payload bistream of the RTPpacket and its size
	 */
	public int getpayload(byte[] data) {

		arraycopy(payload, 0, data, 0, payload_size);

		return (payload_size);
	}

	// --------------------------
	// getpayload_length: return the length of the payload
	// --------------------------
	public int getpayload_length() {
		return (payload_size);
	}

	// --------------------------
	// getlength: return the total length of the RTP packet
	// --------------------------
	public int getlength() {
		return (payload_size + HEADER_SIZE);
	}

	// --------------------------
	// getpacket: returns the packet bitstream and its length
	// --------------------------
	public int getpacket(byte[] packet) {
		// construct the packet = header + payload

		arraycopy(header, 0, packet, 0, HEADER_SIZE);

		arraycopy(payload, 0, packet, 0 + HEADER_SIZE, payload_size);

		// return total size of the packet
		return (payload_size + HEADER_SIZE);
	}

	public int gettimestamp() {
		return (TimeStamp);
	}

	public int getsequencenumber() {
		return (SequenceNumber);
	}

	public int getpayloadtype() {
		return (PayloadType);
	}

}