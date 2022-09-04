import socket
import threading
import types
import json

PORT = 6778
players = []
queue = []
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.bind(('0.0.0.0', PORT))

class Player: # Class for the player info
	def __init__(self, name, addr, conn, pid, tegid):
		self.name = name
		self.addr = addr
		self.conn = conn
		self.pid = pid
		self.tegid = tegid
		self.avatar = 'unassigned'
		self.ready = False
	def to_obj(self): # Turns the class into an object to send out
		o = {
			"player": float(self.pid),
			"name": self.name,
			"avatar": self.avatar,
			"teg_id": self.tegid,
			"is_ready": self.ready
		}
		return o

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
			handle_request(json.loads(msg), conn,addr)
			packet_size = -1

	conn.close()
	print("Connection closed")
	leave_team("","",addr)

def handle_request(data, conn, addr):
	#TODO Don't need data or conn for many of these. So I could probably remove it from the functions
	if data['req'] == 'handshake':
		handshake(data, conn)
	elif data['req'] == 'login':
		login(data, conn, addr)
	elif data['req'] == 'auto_join':
		go_character_select(data, conn, addr)
	elif data['req'] == 'set_avatar':
		select_character(data, conn, addr)
	elif data['req'] == 'set_ready':
		ready_up(data, conn, addr)
	elif data['req'] == 'join_team':
		join_team(data, conn)
	elif data['req'] == 'leave_team':
		leave_team(data,conn,addr)

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

def login(data, conn, addr):
	reply = Packet("login", {"player": float(data['payload']['auth_id']), "teg_id": data['payload']['teg_id'], "name": data['payload']['name']})
	print("Logged in normally!")
	global players
	players.append(Player(data['payload']['name'], addr, conn,data['payload']['auth_id'],data['payload']['teg_id']))
	for p in players:
		print("Player " + p.name)
	send_packet(reply.get_json(), conn)

def go_character_select(data, conn, addr):
	global queue
	global players
	#TODO: Make searching for the player and getting all objs a separate function
	requestingPlayer = ''
	playerQueue = []
	for p in players: #Searches for player that made the request
		if p.addr == addr:
			requestingPlayer = p
	if len(queue) == 0: #If there are no queues, it will make one with the player in it
		queue.append([requestingPlayer])
		playerQueue=queue[len(queue)-1]
	else:
		tries = 0
		for q in queue:
			if len(q) + 1 <= 6:
				q.append(requestingPlayer)
				playerQueue = q
				break
			else:
				tries = tries + 1
		if tries == len(queue): #If user cannot join any queue, it will make a new one
			queue.append([requestingPlayer])
			playerQueue = queue[len(queue)-1]
		else:
			print(tries)
	if len(playerQueue) == 3: #If queue has 3 players, it will send them to champ select
		player_objs = []
		for p in playerQueue:
			player_objs.append(p.to_obj())
		for p in playerQueue:
			reply1 = Packet("match_found", {"countdown":30})
			send_packet(reply1.get_json(),p.conn)
			reply = Packet("team_update", {"players": player_objs, "team":"BLUE"})
			#TODO: Make half the queue on different teams
			send_packet(reply.get_json(),p.conn)
	else:
		for p in playerQueue:
			reply = Packet("queue_update", {"size": len(playerQueue)})
			send_packet(reply.get_json(), p.conn)

def select_character(data, conn, addr):
	global players
	global queue
	playerQueue = []
	player_objs = []
	for q in queue:
		if len(q) == 2:
			for p in q:
				if p.addr == addr: #Searches for user and then switches their character to what they selected
					p.avatar = data['payload']['name']
					playerQueue = q
					break
	for p in playerQueue:
		player_objs.append(p.to_obj())
	for p in playerQueue:
		reply = Packet("team_update", {"players": player_objs, "team":"BLUE"})
		send_packet(reply.get_json(),p.conn)

def ready_up(data, conn, addr):
	global players
	global queue
	playerQueue = []
	player_objs = []
	is_ready = 0
	for q in queue:
		if len(q) == 2:
			for p in q:
				if p.addr == addr:
					playerQueue = q
					p.ready = True
					break
	for p in playerQueue:
		player_objs.append(p.to_obj())
		if p.ready:
			is_ready = is_ready + 1
	for p in playerQueue:
		reply = Packet("team_update", {"players": player_objs, "team": "BLUE"})
		send_packet(reply.get_json(), p.conn)
	if is_ready == len(playerQueue): #Once all players are ready, it will send them to the game.
		for p in playerQueue:
			reply2 = Packet("game_ready", {"countdown": 5, "ip": "127.0.0.1", "port": 9933, "policy_port": 843, "room_id": "notlobby","team": "BLUE", "password": "abc123"})
			send_packet(reply2.get_json(), p.conn)

def join_team(data, conn):
	print("Joined team!")

def leave_team(data,conn,addr):
	global queue
	affected_queue = []
	player_objs = []
	for q in queue:
		for i in range(len(q)):
			if q[i].addr == addr:
				if len(q) == 1: #If player is the only one in queue, it will remove the queue.
					print("Removed queue")
					queue.remove(q)
					break
				q.remove(q[i])
				print ("Removed player")
				affected_queue = q
				break
	for p in affected_queue:
		player_objs.append(p.to_obj())
	for p in affected_queue:
		reply = Packet("queue_update", {"size": len(affected_queue)})
		send_packet(reply.get_json(),p.conn)

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
