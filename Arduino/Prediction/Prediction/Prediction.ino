

/*macro definitions of the sound sensor and the LED*/
#define SOUND_SENSOR A0
#define THRESHOLD_VALUE 400 //The threshold to turn the led on 400.00*5/1024 = 1.95v
#define SPARK_VALUE 770 

int count = 0 ;
int count_spike = 0 ;
int timer = 0; 
int watch = 0;

int count1=0, count2=0, count3=0;
int count_spike1=0, count_spike2=0, count_spike3=0;

void setup()
{
  Serial.begin(115200);
  pins_init();
}


void loop()
{

  watch++;
  timer++;
  
  int sensorValue = analogRead(SOUND_SENSOR);//use A0 to read the electrical signal
  
  if( sensorValue > THRESHOLD_VALUE)
  {
    count++;
  }
  
  if( sensorValue > SPARK_VALUE)
  {
    count_spike++;
  }

//    Serial.println("watch " + String(watch));
    
//    Serial.println("timer " + String(timer));
//    Serial.println("count " + String(count));
//    Serial.println("count_spike " + String(count_spike));

  if(timer >= 6000 )
  {
    int minute_count = watch % 18000;
    
    if (minute_count < 6000)
    {
      count1 = count;
      count_spike1 = count_spike;
    }
    else if (minute_count >= 6000 && minute_count < 12000)
    {
      count2 = count;
      count_spike2 = count_spike;
    }
    else if (minute_count >= 12000)
    {
      count3 = count;
      count_spike3 = count_spike;
    }

    //Serial.println("watch " + watch);
    
    //Serial.println("miunte_count " + String(minute_count));
    ///Serial.println("count " + String(count));
    //Serial.println("count_spike " + String(count_spike));
    //Serial.println("count1 " + String(count1));
    //Serial.println("count2 "+String(count2));
    //Serial.println("count3 "+String(count3));
    //Serial.println("count_spike1 "+String(count_spike1));
    //Serial.println("count_spike2 "+String(count_spike2));
    //Serial.println("count_spike3 "+String(count_spike3));
    
    if( (count_spike1+count_spike2+count_spike3 >= 10) && (count1+count2+count3 >= 350) )
      Serial.println(2); 
    else
      Serial.println(0); 
      
    timer  = 0 ;
    count = 0;
    count_spike = 0;
  }
  
  delay(10);
}

void pins_init()
{
  pinMode(SOUND_SENSOR, INPUT);
}

