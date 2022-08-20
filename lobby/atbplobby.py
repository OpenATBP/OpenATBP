import socket
import threading
import types
import json

PORT = 6778
playerNum = 0
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('0.0.0.0', PORT))

class Packet():
	cmd: str
	payload: types.SimpleNamespace()
	def __init__(self, cmd, payload):
		self.cmd = cmd
		self.payload = payload

	def get_json(self):
		return json.dumps(self.__dict__, separators=(',', ':'))

def handle_client(conn, addr):
	print("New connection")

	connected = True
	packet_size = -1

	while connected:
		if packet_size == -1:
			packet_size = int.from_bytes(conn.recv(2), 'big')
			if packet_size == 0:
				connected = False
		else:
			msg = conn.recv(packet_size).decode('utf-8')
			print("<-", msg)
			handle_request(json.loads(msg), conn)
			packet_size = -1

	conn.close()
	print("Connection closed")
		
def handle_request(data, conn):
	if data['req'] == 'handshake':
		handshake(data, conn)
	elif data['req'] == 'login':
		login(data, conn)
	elif data['req'] == 'auto_join':
		go_character_select(data, conn)
	elif data['req'] == 'set_avatar':
		select_character(data, conn)
	elif data['req'] == 'set_ready':
		ready_up(data, conn)

def send_packet(data, conn):
	print("->", data)

	data_bytes = bytearray(data.encode('utf-8'))
	size = bytearray(len(data_bytes).to_bytes(2, 'big'))

	packet = size + data_bytes
	conn.send(packet)

# Packet handlers
# TODO: move these into seperate source file
def handshake(data, conn):
	reply = Packet("handshake", {"result": True})
	send_packet(reply.get_json(), conn)

def login(data, conn):
	reply = Packet("login", {"player": float(data['payload']['auth_id']), "teg_id": data['payload']['teg_id'], "name": data['payload']['name']})
	print("Logged in normally!")
	print(data)
	send_packet(reply.get_json(), conn)

def go_character_select(data, conn):
	reply = Packet("match_found", {"countdown": 30})
	send_packet(reply.get_json(), conn)

	reply2 = Packet("team_update", {"players":[{"player": 1234, "name":"Guest", "avatar":"unassigned", "teg_id":"Guest", "is_ready": False}], "team":"BLUE"})
	send_packet(reply2.get_json(), conn)

def select_character(data, conn):
	reply = Packet("team_update", {"players":[{"player": 1234, "name":"Guest", "avatar": data['payload']['name'], "teg_id":"Guest", "is_ready": False}], "team":"BLUE"})
	send_packet(reply.get_json(), conn)

def ready_up(data, conn):
	reply = Packet("team_update", {"players":[{"player": 1234, "name":"Guest", "avatar": "finn", "teg_id":"Guest", "is_ready": True}], "team":"BLUE"})
	send_packet(reply.get_json(), conn)

	reply2 = Packet("game_ready", {"countdown": 5, "ip": "127.0.0.1", "port": 9933, "policy_port": 843, "room_id": "notlobby","team": "BLUE", "password": "abc123"})
	send_packet(reply2.get_json(), conn)

# XXX: I don't know if there's a cleaner way to do this. while it does work,
# KeyboardInterrupts won't do anything and the program needs to be killed manually

def start():
	server.listen()
	while True:
		conn, addr = server.accept()
		thread = threading.Thread(target=handle_client, args=(conn, addr))
		thread.start()

if __name__ == "__main__":
	print("DungeonServer running on port %d" % PORT)
	start()
