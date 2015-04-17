package server;

/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */


import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

import util.RTPpacket;
import util.VideoStream;

public class Server extends JFrame implements ActionListener {

    //RTP variables:
    //----------------
    DatagramSocket datagramSocket; //socket to be used to send and receive UDP packets
    DatagramPacket datagramPacket; //UDP packet containing the video frames

    InetAddress ClientIPAddr; //Client IP address
    int RTPDestPort = 0; //destination port for RTP packets  (given by the RTSP Client)

    //GUI:
    //----------------
    JLabel label;

    //Video variables:
    //----------------
    int currentImageNumber = 0; //image nb of the image currently transmitted
    VideoStream videoStream; //VideoStream object used to access video frames
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
    static int VIDEO_LENGTH = 500; //length of the video in frames

    Timer timer; //timer used to send the images at the video frame rate
    byte[] buf; //buffer used to store the images to send to the client

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    //rtsp message types
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    final static int TEARDOWN = 6;

    static int state; //RTSP Server state == INIT or READY or PLAY
    Socket socket; //socket used to send/receive RTSP messages
    //input and output stream filters
    static BufferedReader bufferedReader;
    static BufferedWriter bufferedWriter;
    static String VideoFileName; //video file requested from the client
    static int RTSP_ID = 123456; //ID of the RTSP session
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session

    final static String CRLF = "\r\n";

    //--------------------------------
    //Constructor
    //--------------------------------
    public Server() {

        //init Frame
        super("Server");

        //init Timer
        timer = new Timer(FRAME_PERIOD, this);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        //allocate memory for the sending buffer
        buf = new byte[15000];

        //Handler to close the main window
        addWindowListener(new WindowAdapter() {
            @Override
			public void windowClosing(WindowEvent e) {
                //stop the timer and exit
                timer.stop();
                System.exit(0);
            }
        });

        //GUI:
        label = new JLabel("Send frame #        ", JLabel.CENTER);
        getContentPane().add(label, BorderLayout.CENTER);
    }

    //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception {
        //create a Server object
        Server server = new Server();

        //show GUI:
        server.pack();
        server.setVisible(true);

        //get RTSP socket port from the command line
        int RTSPport = 8000;

        //Initiate TCP connection with the client for the RTSP session
        ServerSocket serverSocket = new ServerSocket(RTSPport);
        server.socket = serverSocket.accept();
        serverSocket.close();

        //Get Client IP address
        server.ClientIPAddr = server.socket.getInetAddress();

        //Initiate RTSPstate
        state = INIT;

        //Set input and output stream filters:
        bufferedReader = new BufferedReader(new InputStreamReader(server.socket.getInputStream()));
        bufferedWriter = new BufferedWriter(new OutputStreamWriter(server.socket.getOutputStream()));

        //Wait for the SETUP message from the client
        int request_type;
        boolean done = false;
        while (!done) {
            request_type = server.parse_RTSP_request(); //blocking

            if (request_type == SETUP) {
                done = true;

                //update RTSP state
                state = READY;
                System.out.println("New RTSP state: READY");

                //Send response
                server.send_RTSP_response();

                //init the VideoStream object:
                server.videoStream = new VideoStream(VideoFileName);

                //init RTP socket
                server.datagramSocket = new DatagramSocket();
            }
        }

        //loop to handle RTSP requests
        while (true) {
            //parse the request
            request_type = server.parse_RTSP_request(); //blocking

            if ((request_type == PLAY) && (state == READY)) {
                //send back response
                server.send_RTSP_response();
                //start timer
                server.timer.start();
                //update state
                state = PLAYING;
                System.out.println("New RTSP state: PLAYING");
            } else if ((request_type == PAUSE) && (state == PLAYING)) {
                //send back response
                server.send_RTSP_response();
                //stop timer
                server.timer.stop();
                //update state
                state = READY;
                System.out.println("New RTSP state: READY");
            } else if (request_type == TEARDOWN) {
                //send back response
                server.send_RTSP_response();
                //stop timer
                server.timer.stop();
                //close sockets
                server.socket.close();
                server.datagramSocket.close();

                System.exit(0);
            }
        }
    }


    //------------------------
    //Handler for timer
    //------------------------
    
    /**
     * This fuction is executed when the timer run each FRAME_PERIOD
     */
    @Override
	public void actionPerformed(ActionEvent e) {

        //if the current image nb is less than the length of the video
        if (currentImageNumber < VIDEO_LENGTH) {
            //update current currentImageNumber
        	currentImageNumber++;

            //get next frame to send from the video, as well as its size
            int image_length = 0;
            try {
            	
                //image_length = videoStream.getnextframe(buf);
				BufferedImage image = videoStream.getNextImage();

				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				ImageIO.write(image, "jpeg", stream);
				stream.flush();
				buf = stream.toByteArray();
				image_length = stream.size();

				stream.close();
                
            } catch (Exception e1) {
                e1.printStackTrace();
            }


            //Builds an RTPpacket object containing the frame
            RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, currentImageNumber, currentImageNumber * FRAME_PERIOD, buf, image_length);
            
            System.out.println("buf = " + buf);
            System.out.println("image_length = " + image_length);

            //get to total length of the full rtp packet to send
            int packet_length = rtp_packet.getlength();


            //retrieve the packet bitstream and store it in an array of bytes
            byte[] packet_bits = new byte[packet_length];
            rtp_packet.getpacket(packet_bits);

            //send the packet as a DatagramPacket over the UDP socket
            datagramPacket = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTPDestPort);
            try {
            	datagramSocket.send(datagramPacket);
            } catch (IOException e1) {
                e1.printStackTrace();
            }

           
            //update GUI
            label.setText("Send frame #" + currentImageNumber);

        } else {
            //if we have reached the end of the video file, stop the timer
            timer.stop();
        }
    }

    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    private int parse_RTSP_request() {
        int request_type = -1;
        try {
            //parse request line and extract the request_type:
            String RequestLine = bufferedReader.readLine();
            //System.out.println("RTSP Server - Received from Client:");

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();

            //convert to request_type structure:
            if ((new String(request_type_string)).compareTo("SETUP") == 0)
                request_type = SETUP;
            else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                request_type = PLAY;
            else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                request_type = PAUSE;
            else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                request_type = TEARDOWN;

            if (request_type == SETUP) {
                //extract VideoFileName from RequestLine
                VideoFileName = tokens.nextToken();
            }

            //parse the SeqNumLine and extract CSeq field
            String SeqNumLine = bufferedReader.readLine();
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());

            //get LastLine
            String LastLine = bufferedReader.readLine();

            if (request_type == SETUP) {
                //extract RTP_dest_port from LastLine
                tokens = new StringTokenizer(LastLine);
                for (int i = 0; i < 3; i++)
                    tokens.nextToken(); //skip unused stuff
                RTPDestPort = Integer.parseInt(tokens.nextToken());
            }
            //else LastLine will be the SessionId line ... do not check for now.
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
        return (request_type);
    }

    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    
    /**
     * This function is used to confirm to the client that the server have receive an instruction.
     * sent RTPSSeqNB, RTPS_ID and ack
     */
    private void send_RTSP_response() {
        try {
            bufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            bufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            bufferedWriter.write("Session: " + RTSP_ID + CRLF);
            bufferedWriter.flush();
            //System.out.println("RTSP Server - Sent response to Client.");
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
}
