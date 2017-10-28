

/*macro definitions of the sound sensor and the LED*/
#define SOUND_SENSOR A0
#define THRESHOLD_VALUE 400 //The threshold to turn the led on 400.00*5/1024 = 1.95v


void setup()
{
  Serial.begin(115200);
  pins_init();
}


void loop()
{
  int sensorValue = analogRead(SOUND_SENSOR);//use A0 to read the electrical signal
  
    
  //send data to raspberry
  if( sensorValue > THRESHOLD_VALUE)
  {
    //Serial.print("time = ");
    //Serial.print(millis());
    //Serial.print( " , sensor value = " );
  
    Serial.println(sensorValue);
  }
  
  delay(10);
}


void pins_init()
{
  pinMode(SOUND_SENSOR, INPUT);
}



