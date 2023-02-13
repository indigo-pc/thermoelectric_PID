package com.ioi.utilities;

import java.math.BigInteger;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

/**
 * Class models the TC-720 TEC control module.
 * 
 * @author Phillip Curtsmith
 * @since Spring 2022
 *
 */
public class TECFixture {

	// Serial connection parameters
	private SerialPort serialPort;
	private final int BAUD_RATE = 230400;
    private String response = null;
    
    // Program values
    private String stringParameter;
    private double temperatureSetpoint = Double.MAX_VALUE;
    private boolean enabled = false;
    private int numericParameter = 0;
    
    // Fixed Commands
    private final String FIXED_DESIRED_CONTROL_SETTING = "1c";
    private final String INPUT_1_TEMPERATURE = "01";
    private final String WRITE_OUTPUT_ENABLE = "30";
    private final String PROPORTIONAL_BANDWIDTH_SET = "1d";
    private final String INTEGRAL_GAIN_SET = "1e";
    private final String DERIVATIVE_GAIN_SET = "1f";
    private final String CARRIAGE_RETURN = "\r";
    
    // PID Tuning values
    private final double PROPORTIONAL_BANDWIDTH = 2.25;
    private final double INTEGRAL_GAIN = 1;
    private final double DERIVATIVE_GAIN = 10;
      
    /**
     * Set the proportional bandwidth (the "P" of PID) to some default value. Factory default is 5C.
     * @throws InterruptedException 
     * @throws SerialPortException 
     */
    private void setProportionalBandwidth( double d ) throws SerialPortException, InterruptedException {
    	numericParameter = (int) (d * 100);
    	stringParameter = Integer.toHexString( numericParameter );
    	while ( stringParameter.length() < 4 ) {
    		stringParameter = '0' + stringParameter;
    	}
    	sendCommand( PROPORTIONAL_BANDWIDTH_SET + stringParameter );
    }
    
    /**
     * Set the integral gain (the "I" of PID) to some default value. Factory default is 1.
     * @throws InterruptedException 
     * @throws SerialPortException 
     */
    private void setIntegralGain( double d ) throws SerialPortException, InterruptedException {
    	numericParameter = (int) (d * 100);
    	stringParameter = Integer.toHexString( numericParameter );
    	while ( stringParameter.length() < 4 ) {
    		stringParameter = '0' + stringParameter;
    	}
    	sendCommand( INTEGRAL_GAIN_SET + stringParameter );
    }
    
    /**
     * Set the derivative gain (the "D" of PID) to some default value. Factory default is 0.
     * @throws InterruptedException 
     * @throws SerialPortException 
     */
    private void setDerivativeGain( double d ) throws SerialPortException, InterruptedException {
    	numericParameter = (int) (d * 100);
    	stringParameter = Integer.toHexString( numericParameter );
    	while ( stringParameter.length() < 4 ) {
    		stringParameter = '0' + stringParameter;
    	}
    	sendCommand( DERIVATIVE_GAIN_SET + stringParameter );
    }
    
    /**
     * For use in client to determine when the actual temperature meets the setpoint.
     * @return True if setpoint reached, else, false.
     * @throws SerialPortException
     * @throws InterruptedException
     */
    public boolean temperatureSettled() throws SerialPortException, InterruptedException {
    	if ( temperatureHysteresis() < 0.2 ) {
    		return true;
    	}
    	return false;
    }
    
    /**
     * Enable the output.
     * @throws SerialPortException
     * @throws InterruptedException
     */
    public void enable() throws SerialPortException, InterruptedException {
    	sendCommand( WRITE_OUTPUT_ENABLE + "0001" );
    	enabled = true;
    }
    
    /**
     * Disable the output.
     * @throws SerialPortException
     * @throws InterruptedException
     */
    public void disable() throws SerialPortException, InterruptedException {
    	sendCommand( WRITE_OUTPUT_ENABLE + "0000" );
    	enabled = false;
    }
    
    /**
     * Compute the percent difference of the setpoint to actual temperature.
     * @return Percent difference between actual TEC temperature and setpoint.
     * @throws SerialPortException
     * @throws InterruptedException
     */
    private double temperatureHysteresis() throws SerialPortException, InterruptedException {
    	return ( Math.abs( this.readTemperatureValue() - this.readTemperatureSetpoint() )
    			/ ( this.readTemperatureSetpoint() + this.readTemperatureValue() ) / 2 )
    			* 100.0;
    }
    
    /**
     * Read the actual temperature of the TEC in that instant.
     * @return Actual temperature at TEC in degrees C. If method returns Double.MAX_VALUE, the temperature has not yet been set by the user.
     * @throws SerialPortException
     * @throws InterruptedException
     */
    public double readTemperatureValue( ) throws SerialPortException, InterruptedException {
    	stringParameter = sendCommand( INPUT_1_TEMPERATURE + "0000" );
    	return Integer.parseInt( stringParameter.substring(1,5), 16 ) / 100.0;
    }
    
    /**
     * Read the temperature setpoint of the TEC.
     * @return User-defined temperature setpoint in degrees C.
     */
    public double readTemperatureSetpoint() {
    	return temperatureSetpoint;
    }
    
    /**
     * Set temperature (degrees C) per manufacturer specifications.
     */
    public void setTemperature( double t ) throws SerialPortException, InterruptedException {
		if ( !enabled ) {
			System.out.println( "TECFixture.java : Attempting to set temperature with fixutre DISABLED!" );
			return;
		}
    	numericParameter = (int) (t * 100);
    	stringParameter = Integer.toHexString( numericParameter );
    	while ( stringParameter.length() < 4 ) {
    		stringParameter = '0' + stringParameter;
    	}
    	temperatureSetpoint = t;
    	sendCommand( FIXED_DESIRED_CONTROL_SETTING + stringParameter );
    }
    
    /**
     * Close serial port.
     * @return True if successful, else, false.
     * @throws Exception
     */
	public boolean closePort() throws Exception {
        return serialPort.closePort();
	}

	/**
	 * Set the serial port address. 
	 * @param port
	 */
	public void setPort( String port ) {
		serialPort = new SerialPort(port);	
	}
	
	/**
	 * Per manufacturer specifications, all commands to/from TC-720 are accompanied by a checksum.
	 * Consists of each individual character in the actual command (String s) converted to hex, those values summed 
	 * (in hex), then the two least significant digits of this sum are reported as the checksum.
	 * 
	 * See Appendix B of TC-720 manual for more details.
	 */
	private String computeCheckSum( String s ) {
		if ( s.length() != 6 ) {
			throw new UnsupportedOperationException( "Incorrect command length." );
		}
		String c1 = Integer.toHexString( s.charAt(0) );
		String c2 = Integer.toHexString( s.charAt(1) );
		String c3 = Integer.toHexString( s.charAt(2) );
		String c4 = Integer.toHexString( s.charAt(3) );
		String c5 = Integer.toHexString( s.charAt(4) );
		String c6 = Integer.toHexString( s.charAt(5) );
		
		String checkSum = 		 new BigInteger( c1, 16).
                			add( new BigInteger( c2, 16) ).
                			add( new BigInteger( c3, 16) ).
                			add( new BigInteger( c4, 16) ).
                			add( new BigInteger( c5, 16) ).
                			add( new BigInteger( c6, 16) ).
                			toString(16);
		
        if ( checkSum.length() == 1 ) {
        	return "0" + checkSum;
        }
		if ( checkSum.length() == 2 ) {
        	return checkSum;
        }
        return checkSum.substring( checkSum.length()-2, checkSum.length() );
	}
	
	public String sendCommand( String s ) throws SerialPortException, InterruptedException {
		response = null;
		serialPort.writeString( "*" + s + computeCheckSum(s) + CARRIAGE_RETURN );
        do {
        	Thread.sleep(5);
        } while ( response == null );
        if ( response.contains( "X" ) ) {
        	// Recursive call assumes that the original command is correct! Else, infinite loop.
        	sendCommand( s );
        }
		return response; 	
	}
    
	/**
	 * Connect to the fixture. Disable the fixture.
	 * @param s : serial port for connection.
	 * @throws Exception
	 * @throws InterruptedException
	 */
	public void connect( String s ) throws Exception, InterruptedException {
		setPort( s );
		serialPort.openPort();
		serialPort.addEventListener( new PortReader() );
		serialPort.setParams( BAUD_RATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE );
		disable();
		// Set tuned PID values
		setProportionalBandwidth( PROPORTIONAL_BANDWIDTH );
		setIntegralGain( INTEGRAL_GAIN );
		setDerivativeGain( DERIVATIVE_GAIN );
	}
	
    /*
     * @see https://arduino.stackexchange.com/questions/3755/how-to-use-readline-from-jssc
     */
	protected class PortReader implements SerialPortEventListener {
		StringBuilder message = new StringBuilder();
		@Override
		public void serialEvent(SerialPortEvent event) {
			if ( event.isRXCHAR() && event.getEventValue() > 0 ) {
				try {
					byte buffer[] = serialPort.readBytes();
					for (byte b : buffer) {
						// Per manufacturer specification, all responses from TEC controller terminate with '^'
						if ( b == '^' ) {
							response = message.toString();
							message.setLength(0);
						} else {
							message.append( (char) b );
						}
					}
				} catch (SerialPortException ex) { ex.printStackTrace(); }
			}
		}
	}
		
	// Testing only
	public static void main(String[] args) throws InterruptedException, Exception {
		TECFixture tec = new TECFixture();
		tec.connect( "/dev/cu.usbserial-AM00G1KA" );
		tec.enable();
		tec.setTemperature( 3.456 );
		tec.closePort();
	}

}
