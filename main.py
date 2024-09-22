from kivy.app import App
from kivy.uix.widget import Widget
from kivy.properties import ObjectProperty
from kivy.lang import Builder
from kivy.core.window import Window

Builder.load_string('''
<MyLayout>:
    BoxLayout:
        orientation: 'vertical'
        size: root.width, root.height
        TextInput:
            id: calc_input
            text: '0'
            halign: 'right'
            font_size: 65
            size_hint: (1, .15)
        GridLayout:
            cols: 4
            rows: 5
            size_hint: (1, .85)
            Button:
                text: '7'
                on_press: root.button_press('7')
            Button:
                text: '8'
                on_press: root.button_press('8')
            Button:
                text: '9'
                on_press: root.button_press('9')
            Button:
                text: '/'
                on_press: root.button_press('/')
            Button:
                text: '4'
                on_press: root.button_press('4')
            Button:
                text: '5'
                on_press: root.button_press('5')
            Button:
                text: '6'
                on_press: root.button_press('6')
            Button:
                text: '*'
                on_press: root.button_press('*')
            Button:
                text: '1'
                on_press: root.button_press('1')
            Button:
                text: '2'
                on_press: root.button_press('2')
            Button:
                text: '3'
                on_press: root.button_press('3')
            Button:
                text: '-'
                on_press: root.button_press('-')
            Button:
                text: 'C'
                on_press: root.clear()
            Button:
                text: '0'
                on_press: root.button_press('0')
            Button:
                text: '='
                on_press: root.calculate()
            Button:
                text: '+'
                on_press: root.button_press('+')
''')

class MyLayout(Widget):
    def button_press(self, button):
        if self.ids.calc_input.text == "0":
            self.ids.calc_input.text = ""
        self.ids.calc_input.text += button

    def clear(self):
        self.ids.calc_input.text = "0"

    def calculate(self):
        try:
            self.ids.calc_input.text = str(eval(self.ids.calc_input.text))
        except:
            self.ids.calc_input.text = "Error"

class CalculatorApp(App):
    def build(self):
        Window.size = (360, 640)  # Set initial window size
        Window.bind(on_resize=self.update_layout)
        return MyLayout()

    def update_layout(self, *args):
        self.root.size = Window.size

if __name__ == '__main__':
    CalculatorApp().run()
