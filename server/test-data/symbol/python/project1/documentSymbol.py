import class1
import class2 as cls2
from funcs import *

p = "lsp"
b = True
func()


class Document_symbol(cls2.Class2):
    def __init__(self):
        self.x = 1
        self.__const = None

    def foo(self, x, y):
        pass

    def bar(self):
        pass


@do_twice
def foo_bar(x, y):
    return class1.Class1()
