import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { MongoProfile } from '../api/types';
import { useAuth } from '../auth/AuthContext';

export function MongoProfilePage() {
  const { user, updateUser } = useAuth();
  const [profile, setProfile] = useState<MongoProfile | null>(null);
  const [uri, setUri] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.mongoProfile().then(setProfile).catch((e) => setError(e.message));
  }, []);

  async function act(label: string, action: () => Promise<MongoProfile>) {
    setBusy(label);
    setError(null);
    try {
      setProfile(await action());
      updateUser(await api.me());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Opération impossible.');
    } finally {
      setBusy(null);
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    await act('save', () => api.saveMongo(uri));
    setUri('');
  }

  async function download() {
    if (!user) return;
    setError(null);
    try {
      await api.downloadMongoDataset(user.datasource);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Téléchargement impossible.');
    }
  }

  if (!profile) {
    return error ? <p className="page form-error">{error}</p> : <div className="page-loading">Chargement…</div>;
  }

  const missing = profile.missingCollections;
  return (
    <div className="page narrow-page">
      <div className="page-head">
        <div>
          <h1>Ma base MongoDB</h1>
          {profile.sharedDatabaseAvailable ? (
            <p className="muted">
              Facultatif : vos requêtes NoSQL s'exécutent sur la base MongoDB de la plateforme. Pour vous exercer à mettre
              en place votre propre base, suivez les étapes ci-dessous (jeu de données{' '}
              {profile.dataset ? <strong>« {profile.dataset} »</strong> : null}).
            </p>
          ) : (
            <p className="muted">
              Pour la partie NoSQL, vos requêtes s'exécutent sur votre propre base MongoDB, dans laquelle vous importez le
              jeu de données {profile.dataset ? <strong>« {profile.dataset} »</strong> : null}.
            </p>
          )}
        </div>
      </div>

      <section className="card mongo-status">
        {profile.configured ? (
          <>
            <p>
              ✅ Base enregistrée : <code>{profile.uri}</code> · base <code>{profile.database}</code>
            </p>
            <div className="toolbar-actions">
              <button type="button" className="button button-ghost" disabled={busy != null} onClick={() => act('verify', api.verifyMongo)}>
                {busy === 'verify' ? 'Vérification…' : 'Vérifier la base'}
              </button>
              <button
                type="button"
                className="button button-ghost"
                disabled={busy != null}
                onClick={() => window.confirm('Oublier cette base MongoDB ?') && act('remove', api.removeMongo)}
              >
                Oublier cette base
              </button>
            </div>
          </>
        ) : (
          <p>
            Aucune base personnelle enregistrée.
            {profile.sharedDatabaseAvailable && ' Vos requêtes s\'exécutent sur la base MongoDB de la plateforme.'}
          </p>
        )}
        {missing != null &&
          (missing.length === 0 ? (
            <p className="ok">Connexion réussie : les {profile.expectedCollections.length} collections du jeu de données sont présentes.</p>
          ) : (
            <p className="notice">
              Connexion réussie, mais {missing.length} collection{missing.length > 1 ? 's sont absentes ou vides' : ' est absente ou vide'} dans
              la base <code>{profile.database}</code> : {missing.join(', ')}. Importez-les (étape 4) puis cliquez sur « Vérifier la base ».
            </p>
          ))}
        {error && <p className="form-error">{error}</p>}
      </section>

      <ol className="steps">
        <li>
          <h3>Créez un cluster gratuit</h3>
          <p>
            Sur <a href="https://www.mongodb.com/cloud/atlas/register" target="_blank" rel="noreferrer">MongoDB Atlas</a>,
            créez un compte puis un cluster <strong>M0 (Free)</strong>.
          </p>
        </li>
        <li>
          <h3>Créez un utilisateur</h3>
          <p>
            Dans <em>Database Access</em>, ajoutez un utilisateur avec un mot de passe (rôle <em>Read and write to any database</em>,
            nécessaire pour l'import). La plateforme ne fera que des lectures.
          </p>
        </li>
        <li>
          <h3>Autorisez la plateforme à se connecter</h3>
          <p>
            Dans <em>Network Access</em>, ajoutez l'adresse <code>0.0.0.0/0</code> (<em>Allow access from anywhere</em>) : les
            serveurs de Learnink n'ont pas d'adresse fixe.
          </p>
        </li>
        <li>
          <h3>Importez le jeu de données</h3>
          <p>
            Téléchargez l'archive : elle contient un fichier JSON par collection. Dans{' '}
            <a href="https://www.mongodb.com/products/tools/compass" target="_blank" rel="noreferrer">MongoDB Compass</a>,
            connectez-vous au cluster, créez la base <code>{profile.database}</code>, puis pour chaque fichier une collection du
            même nom et <em>ADD DATA › Import JSON or CSV file</em>.
          </p>
          <button type="button" className="button button-ghost" onClick={download}>
            ⬇ Télécharger le jeu de données
          </button>
          {profile.expectedCollections.length > 0 && (
            <p className="muted small">Collections attendues : {profile.expectedCollections.join(', ')}.</p>
          )}
        </li>
        <li>
          <h3>Collez la chaîne de connexion</h3>
          <p>
            Dans Atlas, <em>Connect › Drivers</em> : copiez la chaîne <code>mongodb+srv://…</code> et remplacez{' '}
            <code>&lt;db_password&gt;</code> par le mot de passe de l'utilisateur.
          </p>
          <form className="inline-form" onSubmit={save}>
            <label>
              Chaîne de connexion
              <input
                type="password"
                autoComplete="off"
                spellCheck={false}
                placeholder="mongodb+srv://utilisateur:motdepasse@cluster0.xxxxx.mongodb.net/"
                value={uri}
                onChange={(e) => setUri(e.target.value)}
                required
              />
            </label>
            <button className="button button-primary" type="submit" disabled={busy != null}>
              {busy === 'save' ? 'Test de la connexion…' : 'Tester et enregistrer'}
            </button>
          </form>
        </li>
      </ol>
    </div>
  );
}
