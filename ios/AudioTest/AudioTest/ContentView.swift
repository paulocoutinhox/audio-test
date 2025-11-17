import SwiftUI
import AVFoundation
import Combine

// MARK: - ViewModel

final class AudioDebugViewModel: NSObject, ObservableObject {
    
    @Published var inputUrl: String = "https://streams.radiomast.io/ref-128k-mp3-stereo"
    @Published var playerState: PlayerState = .idle
    @Published var logs: [String] = []
    
    private var player: AVPlayer?
    private var statusObserver: NSKeyValueObservation?
    
    enum PlayerState: String {
        case idle = "Idle"
        case loading = "Loading..."
        case playing = "Playing"
        case error = "Error ❌"
    }
    
    override init() {
        super.init()
        addLog("ViewModel initialized")
    }
    
    // MARK: - Logging
    
    private func addLog(_ text: String) {
        DispatchQueue.main.async {
            self.logs.append(text)
            print("[AudioDebug] \(text)")
        }
    }
    
    // MARK: - Controls
    
    func play() {
        logs.removeAll()
        addLog("----- NEW PLAYBACK -----")
        addLog("URL: \(inputUrl)")
        
        guard let url = URL(string: inputUrl) else {
            playerState = .error
            addLog("⚠️ Invalid URL")
            return
        }
        
        playerState = .loading
        stop()
        
        player = AVPlayer(url: url)
        observeStatus()
        
        addLog("Preparing player...")
        player?.play()
    }
    
    func stop() {
        addLog("STOP triggered")
        statusObserver?.invalidate()
        player?.pause()
        player = nil
        playerState = .idle
    }
    
    // MARK: - Observers
    
    private func observeStatus() {
        guard let player = player else { return }
        
        statusObserver = player.currentItem?.observe(\.status, options: [.new, .initial]) { [weak self] item, _ in
            DispatchQueue.main.async {
                switch item.status {
                case .readyToPlay:
                    self?.playerState = .playing
                    self?.addLog("STATE: READY -> playing")
                case .failed:
                    self?.playerState = .error
                    let message = item.error?.localizedDescription ?? "Unknown error"
                    self?.addLog("❌ ERROR: \(message)")
                    if let error = item.error {
                        self?.addDetailedError(error)
                    }
                default:
                    self?.addLog("STATE: \(item.status.rawValue)")
                }
            }
        }
        
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: player.currentItem,
            queue: .main
        ) { [weak self] _ in
            self?.addLog("STATE: ENDED")
        }
    }
    
    private func addDetailedError(_ error: Error) {
        addLog("ERROR TYPE: \(type(of: error))")
        addLog("STACKTRACE:\n\(String(describing: error))")
    }
}

// MARK: - View

struct ContentView: View {
    
    @StateObject private var viewModel = AudioDebugViewModel()
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 14) {
                
                // URL Input
                TextField("Audio URL", text: $viewModel.inputUrl)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .submitLabel(.done)
                    .padding(.horizontal)
                    .toolbar {
                        ToolbarItemGroup(placement: .keyboard) {
                            Spacer()
                            Button("Done") { hideKeyboard() }
                        }
                    }
                
                // Buttons
                HStack {
                    Button(action: { hideKeyboard(); viewModel.play() }) {
                        Text("PLAY")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    
                    Button(action: { hideKeyboard(); viewModel.stop() }) {
                        Text("STOP")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .padding(.horizontal)
                
                // Status
                Text("Status: \(viewModel.playerState.rawValue)")
                    .font(.headline)
                
                // Logs
                Text("Log:")
                    .font(.title3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(Array(viewModel.logs.enumerated()), id: \.offset) { index, log in
                                Text("• \(log)")
                                    .font(.callout)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .id(index)
                            }
                        }
                        .padding(.horizontal)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    .onChange(of: viewModel.logs) { _, _ in
                        if let last = viewModel.logs.indices.last {
                            withAnimation {
                                proxy.scrollTo(last, anchor: .bottom)
                            }
                        }
                    }
                }
                
                Spacer()
            }
            .contentShape(Rectangle())
            .onTapGesture { hideKeyboard() }
            .navigationTitle("Audio Debug Player")
        }
    }
}

// MARK: - Helpers

extension View {
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

// MARK: - Preview

#Preview {
    ContentView()
}
