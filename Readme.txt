To build the project

Run in terminal

javac *.java



To Test,

Open in terminal,

java FTPServer <ServerIP> <ServerPort> <WindowSize> <Interval>

Please note that the interval is in milliseconds and it is recommended to give atleast 5000 as the interval argument.


In another terminal

java FTPClient <FileName>  <ClientIP> <ClientPort> <ServerIP> <ServerPort> <WindowSize> <Interval>

Please note that the interval is in milliseconds and it is recommended to give atleast 5000 as the interval argument.


The file will be downloaded by the client in the same folder with the "_Copy" attached to its name.

Please note that the files to downloaded from the server should be in the same folder as the class files are in.
