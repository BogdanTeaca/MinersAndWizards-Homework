import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

// TEACA BOGDAN

/**
 * Class that implements the channel used by wizards and miners to communicate.
 */
public class CommunicationChannel{
	LinkedBlockingDeque<Message> minersMessageBuffer, wizardsMessageBuffer;
	HashMap<Integer, Message> parentRoomsMessageBufferA, parentRoomsMessageBufferB;
	Semaphore minersChannelSemaphoreA, minersChannelSemaphoreB, wizardsChannelSemaphore;
	HashSet<String> uniqueHashes;
	
	/**
	 * Creates a {@code CommunicationChannel} object.
	 */
	public CommunicationChannel(){
		
		// initializam structurile de date cu care vom face posibila
		// comunicatia intre vrajitori si mineri
		
		// buffer de mesaje al canalului de comunicatie al minerilor. Din acest
		// buffer vrajitorii citesc mesaje, iar minerii scriu mesaje.
		minersMessageBuffer = new LinkedBlockingDeque<Message>();
		
		// buffer de mesaje al canalului de comunicatie al vrajitorilor. Din acest
		// buffer minerii citesc mesaje, iar vrajitorii scriu mesaje.
		wizardsMessageBuffer = new LinkedBlockingDeque<Message>();
		
		// utilizam acest hashmap in functia "putMessageWizardChannel()" pentru a
		// salva primul mesaj (mesajul care contine informatiile despre camera parinte)
		// dintr-o pereche de mesaje <camera parine, camera curenta>.
		parentRoomsMessageBufferA = new HashMap<Integer, Message>();
		
		// utilizam acest hashmap in functia "getMessageWizardChannel()" pentru a
		// salva primul mesaj (mesajul care contine informatiile despre camera parinte)
		// dintr-o pereche de mesaje <camera parine, camera curenta>.
		parentRoomsMessageBufferB = new HashMap<Integer, Message>();
		
		// semafoare utilizate in pereche in functiile "putMessageMinerChannel()" si
		// "getMessageMinerChannel()" cu care ne asiguram ca vrajitorii asteapta sa
		// existe mesaje de la mineri in buffer (in cazul in care vrajitorii "consuma"
		// toate mesajele din buffer, acestia vor astepta pana cand minerii vor "produce"
		// cel putin un mesaj
		minersChannelSemaphoreA = new Semaphore(16);
		minersChannelSemaphoreB = new Semaphore(0);
		
		// semafor utilizat in functiiile "putMessageWizardChannel()" si
		// "getMessageWizardChannel()" cu care ne asiguram ca aceste functii sunt
		// executate de un singur thread la un moment dat
		wizardsChannelSemaphore = new Semaphore(1);
		
		// hashset in care salvam toate hash-urile camerelor intalnite pentru a nu
		// repeta calcularea solutiilor (hash-urilor)
		uniqueHashes = new HashSet<String>();
	}

	/**
	 * Puts a message on the miner channel (i.e., where miners write to and wizards
	 * read from).
	 * 
	 * @param message
	 *            message to be put on the channel
	 */
	public void putMessageMinerChannel(Message message){
		try{
			// decrementam contorul semaforului A. Folosim acest semafor pentru a
			// limita cate mesaje sa se afle maxim in buffer la un moment dat
			
			minersChannelSemaphoreA.acquire();
			
			// adaugam in buffer-ul canalului minerilor mesajul produs de mineri
			
			minersMessageBuffer.addFirst(message);
			
		}catch(InterruptedException e){
		}finally{
			
			// incrementam contorul semaforului B astfel incat vrajitorii sa
			// "stie" ca au cel putin un mesaj pe care sa-l poata prelua/consuma
			
			minersChannelSemaphoreB.release();
		}
	}

	/**
	 * Gets a message from the miner channel (i.e., where miners write to and
	 * wizards read from).
	 * 
	 * @return message from the miner channel
	 */
	public Message getMessageMinerChannel(){
		Message messageToReturn = null;
		
		try {
			// decrementam contorul semaforului B deoarece un vrajitor este
			// pe cale sa consume un mesaj din buffer. Daca acest semafor are
			// valoarea contorului egala cu 0 inainte de apelarea functiei
			// "aquire()", atunci vrajitorii ar fi asteptat pana cand minerii
			// produceau macar un mesaj (sa existe macar un mesaj in buffer
			// pe care vrajitorii sa-l consume)
			
			minersChannelSemaphoreB.acquire();
			
			// scoatem din buffer un mesaj pe care vrajitorii sa-l consume
			
			messageToReturn = minersMessageBuffer.removeLast();
			
		}catch(InterruptedException e){
		}finally{
			// incrementam contorul semaforului A. Folosim acest semafor pentru a
			// limita cate mesaje sa se afle maxim in buffer la un moment dat
			
			minersChannelSemaphoreA.release();
		}
		
		// returnam mesajul pe care sa-l consume vrajitorii
		
		return messageToReturn;
	}

	/**
	 * Puts a message on the wizard channel (i.e., where wizards write to and miners
	 * read from).
	 * 
	 * @param message
	 *            message to be put on the channel
	 */
	public void putMessageWizardChannel(Message message){
		try{
			// decrementam contorul semaforului astfel incat functia sa
			// fie executata de un singur thread la un moment dat in scopul
			// de a pune mesajele in ordine in buffer-ul canalului vrajitorilor.
			// Punerea mesajelor in ordine in acest buffer este importanta
			// intrucat vrajitorii produc mesaje in pereche <camera parinte,
			// camera curenta> care trebuie sa fie citite in ordinea
			// perechii de catre mineri. Altfel, mesajele s-ar amesteca in
			// buffer, iar canalul de comunicare nu ar functiona.
			
			wizardsChannelSemaphore.acquire();
			
			String messageData = message.getData();
			
			// verificam daca am ajuns la capatul unui sir de mesaje produse
			// de catre un vrajitor sau daca am gasit toate solutiile
			
			if(messageData.equals("END") || messageData.equals("EXIT")){
				return;
			}
			
			// daca am ajuns aici, trecand de if-ul de mai sus inseamna ca
			// mesajul primit ca parametru face parte dintr-o pereche
			// <camera parinte, camera curenta>
			
			Integer tid = (int)Thread.currentThread().getId();

			// distribuim mesajele astfel incat fiecare thread vrajitor
			// sa aiba propriul loc temporar in care sa salveze primul
			// mesaj pe care il produce (mesajul care contine informatiile
			// despre camera parinte), astfel incat dupa ce acelasi
			// vrajitor produce si al doilea mesaj din pereche (mesajul
			// care contine informatiile despre camera curenta) sa putem
			// pune in ordine in buffer-ul canalului vrajitorilor perechea
			// de mesaje <camera pereche, camera curenta>
			
			if(!parentRoomsMessageBufferA.containsKey(tid)){
				// daca spatiul temporar alocat vrajitorului cu ID-ul
				// thread-ului "tid" este gol, inseamna ca acest vrajitor
				// nu a produs anterior primul mesaj din perechea curenta de
				// mesaje, deci mesajul primit ca parametru este chiar primul
				// mesaj, deci il punem in acest spatiu temporar al vrajitorului
				// cu ID-ul "tid"
				
				parentRoomsMessageBufferA.put(tid, message);
			}else{
				// daca spatiul temporar alocat vrajitorului contine deja un
				// mesaj inseamna ca mesajul primit ca parametru este al doilea
				// mesaj din perechea curenta de mesaje, deci acum avem perechea
				// completa
				
				if(uniqueHashes.contains(messageData)){
					// verifica daca hash-ul camerei curente se afla deja in lista
					// de hash-uri gasite pana acum (adica daca a fost deja calculat).
					// Daca hash-ul a fost deja calculat atunci nu mai are rost
					// sa-l punem in buffer.
					
					parentRoomsMessageBufferA.remove(tid);
				}else{
					// daca hash-ul camerei curente nu se afla in buffer, inseamna
					// ca trebuie sa gasim solutia acesteia, deci vom adauga hash-ul
					// acestei camere in lista de hash-uri pentru a stii in viitor
					// ca am vizitat aceasta camera
					
					uniqueHashes.add(messageData);
					
					// salvam in buffer-ul canalului vrajitorilor in ordine perechea
					// de mesaje <camera pereche, camera curenta>
					
					wizardsMessageBuffer.addLast(parentRoomsMessageBufferA.remove(tid));
					wizardsMessageBuffer.addLast(message);
				}
			}
		}catch(InterruptedException e){
		}finally{
			// incrementam contorul semaforului pentru a semnaliza altor thread-uri
			// faptul ca thread-ul curent a terminat de executat continutul functiei
			
			wizardsChannelSemaphore.release();
		}
	}

	/**
	 * Gets a message from the wizard channel (i.e., where wizards write to and
	 * miners read from).
	 * 
	 * @return message from the miner channel
	 */
	public Message getMessageWizardChannel(){
		Message messageToReturn = null;
		
		try{
			// decrementam contorul semaforului astfel incat functia sa
			// fie executata de un singur thread la un moment dat in scopul
			// de a scoate mesajele in ordine din buffer-ul canalului vrajitorilor.
			// Scoaterea mesajelor in ordine in acest buffer este importanta
			// intrucat minerii consuma mesaje din pereche <camera parinte,
			// camera curenta> care trebuie sa fie citite in ordinea
			// perechii.
			
			wizardsChannelSemaphore.acquire();
			
			int tid = (int)Thread.currentThread().getId();

			// distribuim mesajele astfel incat fiecare thread miner
			// sa aiba propriul loc temporar in care sa salveze primul
			// mesaj pe care il consuma (mesajul care contine informatiile
			// despre camera parinte), astfel incat dupa ce acelasi
			// miner consuma si al doilea mesaj din pereche (mesajul
			// care contine informatiile despre camera curenta) sa putem
			// oferi in ordine mesajele minerului
			
			if(!parentRoomsMessageBufferB.containsKey(tid)){
				try{
					// daca spatiul temporar alocat minerului cu ID-ul thread-ului
					// "tid" este gol, inseamna ca acest miner nu a solicitat anterior
					// primul mesaj din perechea curenta de mesaje, deci mesajul
					// pe care trebuie sa il oferim este chiar primul mesaj, deci
					// il salvam in acest spatiu temporar al minerului cu ID-ul "tid".
					
					messageToReturn = wizardsMessageBuffer.removeFirst();
					parentRoomsMessageBufferB.put(tid, wizardsMessageBuffer.removeFirst());
				}catch(Exception e){}
			}else{
				// daca spatiul temporar alocat minerului contine deja un
				// mesaj inseamna ca mesajul solicitat de catre acest miner
				// este al doilea mesaj din perechea curenta de mesaje
				
				messageToReturn = parentRoomsMessageBufferB.remove(tid);
			}
		}catch(InterruptedException e){
		}finally{
			// incrementam contorul semaforului pentru a semnaliza altor thread-uri
			// faptul ca thread-ul curent a terminat de executat continutul functiei
			
			wizardsChannelSemaphore.release();
		}
		
		// returnam mesajul pe care minerii sa-l consume
		
		return messageToReturn;
	}
}
