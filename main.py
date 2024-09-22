#إستدعاء المكتبات(الازرار / حقل الادخال/ العلامات/ الواجهة الرئيسية/لون الخلفية)
from kivy.app import App
from kivy.uix.button import Button
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.core.window import Window
from pytube import YouTube

#class box(App):
#	def build(self):
#		lay=BoxLayout(orientation="horizontal")
#		b1=Button(text="abdo1")
#		b2=Button(text="abdo2")
#		b3=Button(text="abdo3")
#		lay.add_widget(b1)
#		lay.add_widget(b2)
#		lay.add_widget(b3)
#		return lay

# تغير لون الخلفية 
Window.clearcolor=(152/255.0,250/255.0,226/255.0,0)

# إنشاء كلاس الواجهة وتوزيع العناصر 
class downloader(BoxLayout):
	def __init__(self, **kwargs):
		#تقسيم الواجهة الرئيسية وتوزيع العناصر عليها 
		super().__init__(**kwargs)
		self.orientation ="vertical"
		self.padding = 28
		self.spacing=20
# العلامات واجهة البرنامج 		
		self.title_label= Label(text="       OCTOPUS \n video downloader", 
												font_size="50sp", 
												color=(45/255.0,114/255.0,143/255.0,10), 
												italic=True, 
												bold=True, 
												opacity=20, 
												pos_hint={'x':0.001}
												)
												
		self.creator_label= Label(text="Maded by: Eng/ Abdo Mahmoud ", 
													font_size="15sp", 
													color=(161/255.0,33/255.0,40/255.0,10), 
													italic=True,
													bold=True
													)
													
		self.output_label= Label(text="", 
													font_size="20sp",
													color=(161/255.0,33/255.0,40/255.0,10)
													)
		
#حقل ادخال الرابط		
		self.video_url= TextInput(hint_text="Enter the video URL to download", 
														height=5, 
														multiline=True, 
														opacity=11,
														size_hint=(1,0.9), 
														pos_hint={'x':0.0, 'y':0.5}
														)
														
		self.output_text=TextInput(multiline=True, 
														opacity=5,
														pos_hint={'x':0.1, 'y':0.15}, 
														size_hint=(0.8,3.0))
														
		#الازرار للتحميل الفيديو
		self.down_button=Button(text="Download",
														height=5, 
														color=(245/255.0,239/255.0,0/255.0,10),
														font_size="28sp", 
														opacity=2, 
														bold=True, 
														size_hint=(0.3,0.9),
														pos_hint={'x':0.33, 'y':0.01},
													    on_release= self.init_state,
														on_press= self.do_action,
														)
		self.rest_button=Button(text="Rest", 
													height=5 ,
													color=(245/255.0,239/255.0,1/255.0,10), 
													font_size="28sp", 
													opacity=2, 
													bold=True, 
													size_hint=(0.3,0.9),
													pos_hint={'x':0.33, 'y':0.05},
												    on_press=self.do_rest,
													)
		
		#عند الضغط علي الازرار 
		#self.down_button.bind(on_press=self.do_action)
    	#self.rest_button.bind(on_press=self.do_rest)
		
		#إضافة العناصر الي الشاشة الرئيسية او الواجهة التطبيق
		self.add_widget(self.title_label)
		self.add_widget(self.creator_label)
		self.add_widget(self.output_text)
		self.add_widget(self.video_url)
	  #self.add_widget(self.output_label)
		self.add_widget(self.down_button)
		self.add_widget(self.rest_button)
		
	def do_action(self,instance):
		try:
			vid=YouTube(url)
			print(vid.title)
			print(vid.thumbnail_url)
			print("="*32)
			video= YouTube(url).streams.all()
			
			v=-1
			for i in video :
			    v=v+1
			    print (str(v),":",str(i))
			    print("-" * 32)
			    
			quality=int(input("choose number for quality:"))
			print("-" * 32)
			print ("Downloading Video......")
			video=video[quality].download ("Download/Videos_download")
			print("-" * 32)
			print (" video is downloaded ")
		except:
			self.output_text.text="sorry, there is Error please try again"	
#تعريف دالة 
	def do_rest(self, instance):
		self.video_url.text=""
		self.output_text.text=""

	def init_state(self, instance):
		 self.video_url.hint_text="Enter the video URL to download"
		 
		 
class Myapp(App):
    def build(self):
    	screen = downloader()
    	return screen
    		
if __name__=="__main__":
	Myapp().run()        
