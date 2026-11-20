import { Component } from 'react';

/** Se uma tela falhar, mostra um aviso com saída em vez de deixar a janela em branco. */
export default class LimiteDeErro extends Component {
  state = { erro: null };
  static getDerivedStateFromError(erro) { return { erro }; }
  render() {
    if (!this.state.erro) return this.props.children;
    return (
      <div className="flex h-full items-center justify-center p-8">
        <div className="vidro-forte w-[min(480px,100%)] rounded-[28px] p-8 text-center">
          <h1 className="text-xl font-semibold">Algo deu errado nesta tela</h1>
          <p className="mt-2 text-sm text-tinta-suave">Seus dados estão salvos. Recarregue para continuar.</p>
          <button type="button" onClick={() => location.reload()} className="mt-5 rounded-full bg-lavanda-solido px-6 py-2.5 text-sm font-semibold text-[#fff]">Recarregar</button>
        </div>
      </div>
    );
  }
}
